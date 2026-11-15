package org.example.simulator;

import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.example.math.Complex;
import org.example.math.ComplexMatrix;
import org.example.math.MathUtils;
import org.example.math.Pair;
import org.example.simulator.algorithm.MottonenStateInitialization;
import org.example.simulator.dto.CircuitAnalyticsDTO;
import org.example.simulator.dto.CircuitStateDetailsDTO;
import org.example.simulator.optimizer.DAGCircuitOptimizer;
import org.example.simulator.register.ClassicalRegister;
import org.example.simulator.register.QuantumRegister;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.SwingWrapper;

import java.text.DecimalFormat;
import java.util.*;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import static org.example.math.MathUtils.isBitSet;
import static org.example.math.MathUtils.normalizeVector;

/// Main class for composing quantum circuits using quantum gates.
///
/// A circuit collects [QuantumTransformation]s in the order they are applied. [#run()] executes the
/// ones that have not been applied yet, so a circuit can be extended and run again without
/// recomputing the part of the state that is already known.
///
/// Qubit `i` of [#qubits] belongs to bit `i` of the state vector index, i.e. the first qubit is
/// the least significant bit. That order shows up in measurement outcomes and in the binary labels
/// of the basis states.
///
/// The state vector holds `2^qubitCount` complex amplitudes, so the qubit count is capped at
/// [#MAX_QUBITS] and both the memory and the run time grow exponentially with it.
public class QuantumCircuit {

    /// The number of qubits this circuit acts on.
    @Getter
    private final int qubitCount;

    /// The state vector, as `2^qubitCount` amplitudes indexed by the basis state they belong to, or
    /// `null` until the circuit is run for the first time.
    ///
    /// The getter returns the array itself, so callers can inspect the amplitudes without copying
    /// them, but can also modify the circuit's state through it.
    @Getter
    Complex[] state;

    /// The classical bits of this circuit, addressed by their circuit-wide index.
    ///
    /// The array grows as classical registers are added, see
    /// [#appendClassicalRegisters(ClassicalRegister...)].
    private boolean[] classicalRegisters = new boolean[0];

    /// The source of randomness for [#measure(int)] and [#measureOnce()].
    ///
    /// It is not seeded, so measurements are not reproducible across runs.
    private final Random random = new Random();

    /// The maximum number of qubits in a circuit, since the state vector needs `2^n` amplitudes.
    public static final int MAX_QUBITS = 20;

    /// The number of decimal places the `print*` methods use for amplitudes, magnitudes and
    /// probabilities.
    public static final int PRINT_PRECISION = 3;

    /// The transformations of this circuit in the order they are applied.
    ///
    /// [#run()] applies the ones behind [#nAppliedTransformations], so appending a gate to the
    /// list is enough to extend a circuit that has already been run.
    @Setter
    @Getter
    private List<QuantumTransformation> transformations = new ArrayList<>(); // TODO make DAG representation the default

    /// The register holding every qubit of this circuit.
    @Getter
    private final QuantumRegister qubits;

    /// Maps each qubit of this circuit onto its bit index in the state vector, where index `0` is the
    /// least significant bit.
    ///
    /// The map compares qubits by identity, so a [Qubit] that is not part of [#qubits] is not
    /// found, even when it was created the same way.
    final Map<Qubit, Integer> qubitToIndexMap = new IdentityHashMap<>();

    /// The counters and timings of the operations on this circuit, e.g. the number of state vector
    /// operations or the time spent optimizing. Printed by [#printAnalytics()].
    final CircuitAnalyticsDTO analyticsDTO = new CircuitAnalyticsDTO();

    /// Creates a circuit of `qubitCount` qubits with a new register.
    ///
    /// @throws IllegalArgumentException if `qubitCount` is less than 1 or more than [#MAX_QUBITS]
    public QuantumCircuit(int qubitCount) {
        this.qubitCount = qubitCount;
        this.qubits = new QuantumRegister(this.qubitCount);
        for (int i = 0; i < this.qubitCount; i++) {
            this.qubitToIndexMap.put(qubits.get(i), i);
        }
    }

    /// Creates a circuit spanning the given registers.
    ///
    /// @param regs the registers to combine, in the order they are given
    /// @throws IllegalArgumentException if no register is given or the registers hold more than
    ///                                  [#MAX_QUBITS] qubits in total
    ///
    /// @apiNote Registers are in order from least significant to most significant bits.
    public QuantumCircuit(QuantumRegister... regs) {

        this.qubits = QuantumRegister.combine(regs);
        this.qubitCount = this.qubits.size();

        for (int i = 0; i < this.qubitCount; i++) {
            this.qubitToIndexMap.put(qubits.get(i), i);
        }
    }

    /// Creates a deep copy of `other`.
    ///
    /// The transformations, the state vector and the classical bits are copied, so writing to one
    /// circuit does not change the other, and the number of applied transformations is carried over.
    /// Both circuits still share the same [Qubit] objects, because the copy uses the same register.
    ///
    /// @param other the circuit to copy
    public QuantumCircuit(QuantumCircuit other) {
        this.qubits = other.qubits;
        this.qubitCount = other.qubitCount;

        this.transformations.addAll(other.transformations
                .stream()
                .map(QuantumTransformation::new)
                .toList());

        if (other.state != null) {
            this.state = new Complex[1 << qubitCount];
            for (int i = 0; i < other.state.length; i++)
                this.state[i] = new Complex(other.state[i]);
        }

        this.nAppliedTransformations = other.nAppliedTransformations;
        this.classicalRegisters = other.classicalRegisters.clone();

        for (int i = 0; i < this.qubitCount; i++) {
            this.qubitToIndexMap.put(qubits.get(i), i);
        }
    }

    /// Runs this circuit without optimization.
    ///
    /// Equivalent to calling `QuantumCircuit.run(false)`.
    public void run() {
        // FIXME optimization is disabled by default until I can make optimization + runtime faster than just unoptimized runtime
        this.run(false);
    }

    /// The number of transformations that have already been applied to [#state], i.e. the index of
    /// the first transformation that [#run()] still has to execute.
    int nAppliedTransformations = 0;

    /// Runs this circuit.
    ///
    /// Only the transformations behind [#nAppliedTransformations] are executed, so running a
    /// circuit again after appending gates only computes the newly added part of the state.
    ///
    /// @param optimizeCircuit whether to rewrite the transformations that were not applied yet
    ///                        before executing them, see
    ///                        [DAGCircuitOptimizer#optimize(QuantumCircuit, int, int)]
    ///
    /// @apiNote Currently, it is usually faster to run the circuit without also running optimization.
    public void run(boolean optimizeCircuit) {
        if (this.state == null)
            this.prepareIdentityState();

        if (optimizeCircuit) {
            long start = System.currentTimeMillis();

            DAGCircuitOptimizer.optimize(this, nAppliedTransformations, DAGCircuitOptimizer.UNLIMITED_DEPTH);

            long end = System.currentTimeMillis();
            analyticsDTO.optimizationTimeMillis += end - start;
        }

        long start = System.currentTimeMillis();

        for (int i = nAppliedTransformations; i < transformations.size(); i++) {
            QuantumTransformation tr = transformations.get(i);

            Set<Qubit> controls = tr.getControls();
            if (controls.isEmpty())
                this.transform(tr.getGate(), tr.getTarget());
            else {
                this.mcTransform(tr.getGate(), controls, tr.getTarget());
            }
        }
        nAppliedTransformations = transformations.size();

        long end = System.currentTimeMillis();
        analyticsDTO.executionTimeMillis += end - start;
    }


    /// Applies a single qubit gate to every pair of amplitudes that differs in the target bit.
    private void transform(Gate gate, Qubit target) {

        int targetIndex = qubitToIndexMap.get(target);
        int targetMask = 1 << targetIndex;

        for (int i = 0; i < (1 << qubitCount) - 1; i++) {
            if ((i & targetMask) != 0)
                continue;

            applyGate(i, i | targetMask, gate.getMatrix());
        }
    }

    /// Multi-controlled transform.
    private void mcTransform(Gate gate, Set<Qubit> controls, Qubit target) {

        int targetIndex = qubitToIndexMap.get(target);
        int targetMask = 1 << targetIndex;

        int controlMask = 0;
        for (Qubit control : controls) {
            int controlIndex = qubitToIndexMap.get(control);
            controlMask |= (1 << controlIndex);
        }

        for (int i = 0; i < (1 << qubitCount) - 1; i++) {
            if ((i & targetMask) != 0)
                continue;

            if ((i & controlMask) != controlMask)
                continue;

            applyGate(i, i | targetMask, gate.getMatrix());
        }
    }

    /// Applies the 2x2 matrix of a gate to the amplitudes `k0` and `k1` of a basis state pair.
    private void applyGate(int k0, int k1, ComplexMatrix gateMatrix) {
        analyticsDTO.statevectorOperations++;

        Complex c0 = state[k0];
        Complex c1 = state[k1];

        state[k0] = c0.multiply(gateMatrix.get(0, 0)).add(c1.multiply(gateMatrix.get(0, 1)));
        state[k1] = c0.multiply(gateMatrix.get(1, 0)).add(c1.multiply(gateMatrix.get(1, 1)));
    }

    /// Allocates the state vector and puts it into the `|0...0>` state.
    private void prepareIdentityState() {
        int size = 1 << qubitCount;
        this.state = new Complex[size];

        state[0] = Complex.ONE;
        for (int i = 1; i < size; i++) {
            state[i] = Complex.ZERO;
        }
    }

    /// Returns the inverse of this circuit.
    ///
    /// Any circuit `A` composed with `A.inverse()` is equivalent to an empty circuit.
    ///
    /// `A^-1 A = I`
    ///
    /// The gates are inverted in reverse order and keep their controls and targets, so the inverse
    /// acts on the same qubits as the original circuit.
    ///
    /// @return a new circuit that undoes the transformations of this one
    public QuantumCircuit inverse() {
        QuantumCircuit inverted = new QuantumCircuit(this.qubits);

        for (int i = this.transformations.size() - 1; i >= 0; i--) {
            inverted.transformations.add(this.transformations.get(i).inverse());
        }

        return inverted;
    }

    /// Appends the specified circuit's gates to this circuit.
    ///
    /// @apiNote Uses the whole circuit register as the target register.
    /// @implNote See {@link #append(QuantumCircuit, QuantumRegister...)} for the qubit mapping rules.
    public void append(QuantumCircuit other) {
        appendCore(null, other, qubits);
    }

    /// Appends the specified circuit's gates to this circuit on the specified registers.
    ///
    /// Qubit mapping rules:
    /// - If every qubit of `other` is contained in the target registers, the gates are appended without
    ///   remapping, i.e. `other`'s operations keep acting on the exact same qubits.
    /// - Otherwise, if any qubit of `other` belongs to this circuit, an exception is thrown,
    ///   as partially overlapping qubits make the mapping ambiguous.
    /// - Otherwise (fully disjoint qubits), `other`'s qubits are mapped positionally:
    ///   the i-th qubit of `other` is mapped onto the i-th qubit of the combined target register.
    ///
    /// @param other the circuit whose transformations are appended
    /// @param regs  the registers the transformations of `other` are mapped onto
    /// @throws IllegalArgumentException if the target registers are too small, if `other` acts on a
    ///                                  qubit outside its registers, or if `other` only partially
    ///                                  overlaps with this circuit
    ///
    /// @apiNote The order of the registers matters for which qubits the gates get applied to.
    public void append(QuantumCircuit other, QuantumRegister... regs) {
        appendCore(null, other, regs);
    }

    /// Appends the specified circuit's gates to this circuit on the specified registers, controlled on `control` qubit.
    ///
    /// @param control the qubit every appended transformation is controlled on
    /// @param other   the circuit whose transformations are appended
    /// @param regs    the registers the transformations of `other` are mapped onto
    /// @throws IllegalArgumentException if the target registers are too small, if `other` acts on a
    ///                                  qubit outside its registers, or if `other` only partially
    ///                                  overlaps with this circuit
    ///
    /// @apiNote The order of the registers matters for which qubits the gates get applied to.
    /// @implNote See {@link #append(QuantumCircuit, QuantumRegister...)} for the qubit mapping rules.
    public void cAppend(Qubit control, final QuantumCircuit other, QuantumRegister... regs) {
        appendCore(control, other, regs);
    }

    /// Implements [#append(QuantumCircuit, QuantumRegister...)] and its controlled variant.
    ///
    /// @param control the qubit every appended gate is controlled on, or `null` to append the gates
    ///                unchanged
    private void appendCore(Qubit control, final QuantumCircuit other, QuantumRegister... regs) {

        // default to the whole circuit register, e.g. for `append(other)`
        if (regs.length == 0)
            regs = new QuantumRegister[]{qubits};

        QuantumRegister targetReg = QuantumRegister.combine(regs);

        if (targetReg.size() < other.qubitCount)
            throw new IllegalArgumentException("Appended quantum circuit doesn't fit in this register.\n" +
                    "targetReg's qubits = " + targetReg.size() +
                    ", other's qubits = " + other.qubitCount);

        Set<Qubit> targetQubits = Collections.newSetFromMap(new IdentityHashMap<>());
        targetQubits.addAll(targetReg.getQubits());

        if (targetQubits.containsAll(other.qubits.getQubits())) {
            appendWithoutRemapping(control, other);
            return;
        }

        // qubits shared with this circuit must be fully covered by the target registers,
        // otherwise the mapping would be ambiguous
        List<Qubit> otherQubits = other.qubits.getQubits();
        for (int i = 0; i < otherQubits.size(); i++) {
            if (qubitToIndexMap.containsKey(otherQubits.get(i)))
                throw new IllegalArgumentException("Appended quantum circuit partially overlaps with this circuit.\n" +
                        "Offending qubit index = " + i +
                        ", targetReg's qubits = " + targetReg.size() +
                        ", other's qubits = " + other.qubitCount);
        }

        appendWithPositionalMapping(control, other, targetReg);
    }

    /// Appends the transformations of `other` 1:1, keeping the exact same qubits.
    private void appendWithoutRemapping(Qubit control, final QuantumCircuit other) {

        Set<Qubit> otherQubits = Collections.newSetFromMap(new IdentityHashMap<>());
        otherQubits.addAll(other.qubits.getQubits());

        List<QuantumTransformation> copies = new ArrayList<>(other.transformations.size());
        for (QuantumTransformation tr : other.transformations) {
            if (!otherQubits.contains(tr.getTarget()) || !otherQubits.containsAll(tr.getControls()))
                throw new IllegalArgumentException("Appended quantum circuit acts on a qubit outside of its registers.\n" +
                        "offending transformation = " + tr);

            QuantumTransformation copy = new QuantumTransformation(tr);
            if (control != null) copy.addControl(control);
            copies.add(copy);
        }

        this.transformations.addAll(copies);
    }

    /// Appends the transformations of `other`, mapping `other`'s i-th qubit onto the i-th qubit of `targetReg`.
    private void appendWithPositionalMapping(Qubit control, final QuantumCircuit other, QuantumRegister targetReg) {

        Map<Qubit, Qubit> remap = new IdentityHashMap<>();
        for (int i = 0; i < other.qubitCount; i++) {
            remap.put(other.qubits.get(i), targetReg.get(i));
        }

        Set<Qubit> remappedQubits = remap.keySet();

        List<QuantumTransformation> copies = new ArrayList<>(other.transformations.size());
        for (QuantumTransformation tr : other.transformations) {
            if (!remappedQubits.contains(tr.getTarget()) || !remappedQubits.containsAll(tr.getControls()))
                throw new IllegalArgumentException("Appended quantum circuit acts on a qubit outside of its registers.\n" +
                        "offending transformation = " + tr);

            copies.add(tr.remapped(remap));
        }

        if (control != null)
            copies.forEach(copy -> copy.addControl(control));

        this.transformations.addAll(copies);
    }

    /// Returns a snapshot of the circuit's classical bits.
    public List<Boolean> getClassicalRegisters() {
        List<Boolean> values = new ArrayList<>(classicalRegisters.length);
        for (boolean bit : classicalRegisters)
            values.add(bit);
        return values;
    }

    /// Returns the value of a classical bit by its circuit-wide index.
    ///
    /// @throws ArrayIndexOutOfBoundsException if no bit with this index was allocated, see
    ///                                        [#appendClassicalRegisters(ClassicalRegister...)]
    public boolean getClassicalBit(int index) {
        return classicalRegisters[index];
    }

    /// Returns the integer value of a classical register, with bit 0 as its least significant bit.
    public int getClassicalRegisterValue(ClassicalRegister register) {
        int value = 0;
        for (int i = 0; i < register.size(); i++) {
            if (getClassicalBit(register.get(i)))
                value |= 1 << i;
        }
        return value;
    }

    /// Returns the integer represented by all classical bits, with bit 0 as the least significant bit.
    public int getClassicalRegisterValue() {
        int value = 0;
        for (int i = 0; i < classicalRegisters.length; i++) {
            if (classicalRegisters[i])
                value |= 1 << i;
        }
        return value;
    }

    /// Adds classical registers to this circuit and allocates zero-initialized storage for their bits.
    ///
    /// The bit indices of the registers are shifted to follow the bits that were already allocated,
    /// so a register keeps addressing the same circuit-wide bits after more registers are added.
    ///
    /// @param cRegs the registers to add, in the order their bits should follow each other
    public void appendClassicalRegisters(ClassicalRegister... cRegs) {
        int totalLength = classicalRegisters.length;
        for (ClassicalRegister cReg : cRegs)
            totalLength += cReg.size();

        boolean[] expandedRegisters = Arrays.copyOf(classicalRegisters, totalLength);
        int offset = classicalRegisters.length;
        for (ClassicalRegister cReg : cRegs) {
            cReg.shift(offset);
            offset += cReg.size();
        }
        classicalRegisters = expandedRegisters;
    }

    /// Performs measurements of the whole circuit and returns a list of pairs (value, # of measurements).
    ///
    /// @param samples the number of measurements to draw
    ///
    /// @apiNote See [#measure(int, QuantumRegister...)] for the format of the values.
    public List<Pair<String, Integer>> measure(int samples) {
        return measure(samples, qubits);
    }

    /// Performs measurements of the specified registers and returns a list of pairs (value, # of measurements)
    /// sorted decreasing by the number of measurements.
    ///
    /// Each value is a binary string of the measured qubits, most significant qubit first, where the
    /// most significant qubit is the last one of the combined registers. Measuring the whole circuit
    /// therefore returns the same bit order as the binary representation of a basis state. The state
    /// itself is not changed.
    ///
    /// @param samples the number of measurements to draw
    /// @param regs    the registers to measure, in order from the least to the most significant bit
    public List<Pair<String, Integer>> measure(int samples, QuantumRegister... regs) {
        QuantumRegister targetReg = QuantumRegister.combine(regs);
        int targetQubitCount = targetReg.size();

        double[] probs = getProbabilities(targetReg);
        double[] cumulativeProbs = new double[probs.length];
        double cumulative = 0.0;

        for (int state = 0; state < probs.length; state++) {
            cumulative += probs[state];
            cumulativeProbs[state] = cumulative;
        }

        String[] measurementNames = new String[probs.length];

        for (int state = 0; state < measurementNames.length; state++)
            measurementNames[state] = MathUtils.toBinaryString(state, targetQubitCount);

        Map<String, Integer> measurements = new HashMap<>();

        for (int sample = 0; sample < samples; sample++) {
            double randomValue = random.nextDouble();

            // Find the first cumulative probability strictly greater than the random value.
            int low = 0;
            int high = cumulativeProbs.length - 1;

            while (low < high) {
                int middle = (low + high) >>> 1;

                if (randomValue < cumulativeProbs[middle]) {
                    high = middle;
                } else {
                    low = middle + 1;
                }
            }

            measurements.merge(measurementNames[low], 1, Integer::sum);
        }

        return measurements.entrySet()
                .stream()
                .map(entry -> new Pair<>(entry.getKey(), entry.getValue()))
                .sorted(
                        Comparator
                                .comparing(
                                        (Pair<String, Integer> pair) -> pair.second(),
                                        Comparator.reverseOrder())
                                .thenComparing(Pair::first)
                )
                .collect(Collectors.toList());
    }

    /// Performs one measurement of the whole circuit and returns the measured value.
    ///
    /// @return the measured basis state, with the first qubit as its least significant bit
    ///
    /// @apiNote The state itself is not changed, see [#measureTransformation(Qubit, int)] to also
    /// collapse it.
    public int measureOnce() {
        return this.measureOnce(qubits);
    }

    /// Performs one measurement of the specified registers and returns the measured value.
    ///
    /// @param regs the registers to measure, in order from the least to the most significant bit
    /// @return the measured outcome as an integer, indexed by the qubits of the combined registers
    public int measureOnce(QuantumRegister... regs) {
        QuantumRegister targetReg = QuantumRegister.combine(regs);

        double[] probabilities = getProbabilities(targetReg);

        double rng = random.nextDouble();
        double total = 0.0;
        for (int i = 0; i < probabilities.length; i++) {
            total += probabilities[i];
            if (total > rng) {
                return i;
            }
        }

        throw new RuntimeException("measureOnce failed");
    }

    /// Returns an array of probabilities of all states in the circuit.
    public double[] getProbabilities() {
        return getProbabilities(qubits);
    }

    /// Returns an array of probabilities of all states in the circuit.
    ///
    /// Registers passed into `regs` will be ordered right to left in the result.
    public double[] getProbabilities(QuantumRegister... regs) {

        QuantumRegister targetReg = QuantumRegister.combine(regs);
        double[] probs = new double[1 << targetReg.size()];

        for (int i = 0; i < state.length; i++) {
            // probability of this basis state
            double p = state[i].absSquared();

            // extract target bits and build outcome index
            int outcome = 0;
            for (int t = 0; t < targetReg.size(); t++) {

                // get target index
                int target = qubitToIndexMap.get(targetReg.get(t));

                // extract the bit corresponding to target qubit
                int bit = (i >> target) & 1;

                // perform OR with the rest of the desired bits
                // effectively: squish the full outcome index to just the target bits
                outcome |= (bit << t);
            }

            probs[outcome] += p;
        }

        return probs;
    }

    /// Prints the probabilities of measuring each outcome, one `outcome: probability` line per state.
    ///
    /// @param regs the registers to measure; unlike [#getProbabilities()], at least one register is
    ///             required, and the outcomes are ordered by the combined registers
    ///
    /// @throws IllegalArgumentException if no register is given
    public void printProbabilities(QuantumRegister... regs) {
        DecimalFormat df = new DecimalFormat("#." + "#".repeat(PRINT_PRECISION));

        double[] probs = getProbabilities(regs);

        for (int i = 0; i < probs.length; i++) {
            System.out.print(i + ": " + df.format(probs[i]) + "\n");
        }
    }

    /// Prints the state vector, one `outcome: amplitude` line per basis state.
    ///
    /// Both parts of the amplitude are rounded to [#PRINT_PRECISION] decimal places.
    public void printState() {
        DecimalFormat df = new DecimalFormat("#." + "#".repeat(PRINT_PRECISION));

        for (int i = 0; i < this.state.length; i++) {
            System.out.print(i + ": " +  this.state[i].toString(df) + "\n");
        }
    }

    /// Prints the state of this circuit with all columns, see [#printStateDetailed(boolean)].
    ///
    /// Equivalent to calling `QuantumCircuit.printStateDetailed(false)`, so every state is printed.
    public void printStateDetailed() {
        printStateDetailed(false);
    }

    /// Prints the state of this circuit with additional info.
    ///
    /// - `Outcome` - state's decimal representation
    /// - `Binary` - state's binary representation
    /// - `Amplitude` - state's amplitude as a complex number
    /// - `Direction` - state's global phase
    /// - `Magnitude` - state's magnitude
    /// - `Probability` - probability of measuring this state (equal to `magnitude^2`)
    ///
    /// @param nonZeroOnly prints only states with non-zero amplitudes up to {@link QuantumCircuit#PRINT_PRECISION} decimal places.
    public void printStateDetailed(boolean nonZeroOnly) {
        String[] columnNames = {"Outcome", "Binary", "Amplitude", "Direction", "Magnitude", "Probability"};

        List<CircuitStateDetailsDTO> detailsList = new ArrayList<>();
        DecimalFormat df = new DecimalFormat("#." + "#".repeat(PRINT_PRECISION));

        for (int i = 0; i < this.state.length; i++) {
            Complex c = this.state[i];
            CircuitStateDetailsDTO dto = new CircuitStateDetailsDTO();

            dto.outcome = String.valueOf(i);
            dto.binary = String.format(
                    "%" + qubitCount + "s",
                    Integer.toBinaryString(i)
            ).replace(' ', '0');

            dto.amplitude = c.toString(df);
            dto.direction = df.format(c.directionDegrees());
            dto.magnitude = df.format(c.abs());
            dto.probability = df.format(c.absSquared());

            detailsList.add(dto);
        }

        int[] maxColumnWidths = new int[columnNames.length];

        for (int i = 0; i < columnNames.length; i++) {
            maxColumnWidths[i] = columnNames[i].length();
        }
        for (val details : detailsList) {
            for (int i = 0; i < maxColumnWidths.length; i++) {
                maxColumnWidths[i] = Math.max(details.getString(i).length(), maxColumnWidths[i]);
            }
        }

        StringBuilder builder = new StringBuilder("| ");
        for (int i = 0; i < columnNames.length; i++) {
            builder.append(columnNames[i])
                    .repeat(" ", Math.max(0, maxColumnWidths[i] - columnNames[i].length()))
                    .append(" | ");
        }
        System.out.println(builder);

        for (val details : detailsList) {
            if (nonZeroOnly && Objects.equals(details.getMagnitude(), "0"))
                continue;

            builder = new StringBuilder("| ");
            for (int i = 0; i < columnNames.length; i++) {
                builder.append(details.getString(i))
                        .repeat(" ", Math.max(0, maxColumnWidths[i] - details.getString(i).length()))
                        .append(" | ");
            }
            System.out.println(builder);
        }
    }

    /// Prints analytics about this quantum circuit.
    ///
    /// @apiNote Some data (e.g. execution time) will be missing if called before {@code run()}.
    public void printAnalytics() {

        analyticsDTO.qubitCount = qubitCount;
        analyticsDTO.transformations = transformations.size();
        analyticsDTO.controlledTransformations = (int) transformations.stream()
                .filter(t -> !t.getControls().isEmpty())
                .count();

        System.out.println(analyticsDTO);
    }

    /// Opens a window with a bar chart of the probabilities of measuring each outcome.
    ///
    /// There is one bar per basis state of the combined registers, labeled with the bits of that
    /// state in the same order as the binary strings returned by [#measure(int, QuantumRegister...)].
    ///
    /// @param regs the registers to chart, ordered right to left as described in
    ///             [#getProbabilities(QuantumRegister...)]; defaults to all qubits of the circuit
    public void displayProbabilityChart(QuantumRegister... regs) {

        double[] probs = regs.length == 0 ? getProbabilities() : getProbabilities(regs);
        int bitCount = Integer.numberOfTrailingZeros(probs.length);

        List<String> labels = new ArrayList<>(probs.length);
        List<Double> values = new ArrayList<>(probs.length);
        for (int i = 0; i < probs.length; i++) {
            String format = "%" + bitCount + "s";
            labels.add(String.format(format, Integer.toBinaryString(i)).replace(' ', '0'));
            values.add(probs[i]);
        }

        CategoryChart chart = new CategoryChartBuilder()
                .width(1000)
                .height(500)
                .title("State probabilities")
                .xAxisTitle("Basis state")
                .yAxisTitle("Probability")
                .build();
        chart.addSeries("probability", labels, values);
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setPlotGridVerticalLinesVisible(false);

        new SwingWrapper<>(chart).displayChart();
    }

    /// Multiplies every amplitude of the state by `e^(i * angle)`.
    ///
    /// A global phase does not change any measurement outcome, but the state vector itself differs,
    /// which matters when it is compared against another state.
    ///
    /// @param angle the phase in radians
    ///
    /// @implNote Implemented by appending `PHASE(angle) X PHASE(angle) X` on the first qubit, so it
    /// takes effect when the circuit runs and the added transformations stay part of the circuit.
    public void applyGlobalPhase(double angle) {
        Qubit q = qubits.getFirst();
        this.phase(angle, q);
        this.x(q);
        this.phase(angle, q);
        this.x(q);
    }

    /// Applies a uniform superposition over all states.
    ///
    /// Equivalent to applying the Hadamard gate `H` to all qubits in the circuit.
    public void uniform() {
        this.h(qubits);
    }

    /// Applies a geometric sequence of phase shifts on all states.
    ///
    /// For every state `|x>`, the applied phase shift will be `x * theta`.
    ///
    /// @param theta the phase difference between two neighboring states
    /// @param targetReg the register the phases are applied to
    /// @param reverse order of qubits will be reversed
    public void geometric(double theta, QuantumRegister targetReg, boolean reverse) {
        int count = targetReg.size();

        for (int i = 0; i < count; i++) {
            int exponent = reverse ? count - 1 - i : i;
            this.phase((1 << exponent) * theta, targetReg.get(i));
        }
    }

    /// Prepares the specified state using CNOTs and RY, RZ gates. Expected to run on the identity statevector.
    ///
    /// Every listed basis state gets the same amplitude, so `{0, 3}` prepares an equal superposition
    /// of those two states.
    ///
    /// @param reg    the register to prepare; it has to hold all qubits of the state
    /// @param values the basis state indices that get an amplitude, each of which has to fit into
    ///               `reg`, so it has to be less than `2^reg.size()`
    /// @throws ArrayIndexOutOfBoundsException if a value does not fit into `reg`
    public void initializeWithValues(QuantumRegister reg, int[] values) {
        Complex[] state = new Complex[1 << reg.size()];

        Arrays.fill(state, Complex.ZERO);
        for (int v : values) {
            state[v] = Complex.ONE;
        }

        MottonenStateInitialization.perform(this, reg, state);
    }

    /// Prepares the specified state using Möttönen's Method.
    ///
    /// Expected to run as the first operation on the circuit (i.e. requires the statevector to be in the |0> state).
    ///
    /// Both the magnitudes and the relative phases of the amplitudes are reproduced, and the mean
    /// phase of the input is applied as a global phase.
    ///
    /// @param reg   the register to prepare; it has to hold all qubits of the state
    /// @param state the amplitudes to prepare, in basis state order; the amplitudes do not have to be
    ///              normalized, but the array has to hold `2^reg.size()` of them
    public void initializeWithState(QuantumRegister reg, Complex[] state) {
        MottonenStateInitialization.perform(this, reg, state);
    }

    /// Applies Quantum Fourier Transform to selected qubits.
    ///
    /// @param targetReg the register to transform
    /// @param reverse expect swapped qubit order in the input
    /// @param swap    preserve qubit order using additional SWAP gates
    public void qft(QuantumRegister targetReg, boolean reverse, boolean swap) {
        _qft(false, targetReg, reverse, swap);
    }

    /// Applies Inverse Quantum Fourier Transform to selected qubits.
    ///
    /// @param targetReg the register to transform
    /// @param reverse expect swapped qubit order in the input
    /// @param swap    preserve qubit order using additional SWAP gates
    public void iqft(QuantumRegister targetReg, boolean reverse, boolean swap) {
        _qft(true, targetReg, reverse, swap);
    }

    /// Applies the Quantum Fourier Transform or its inverse to `targetReg`.
    ///
    /// @param inverse applies the inverse transform, i.e. phases are negated
    /// @param targetReg the register to transform
    /// @param reverse expect swapped qubit order in the input
    /// @param swap    preserve qubit order using additional SWAP gates
    private void _qft(boolean inverse, QuantumRegister targetReg, boolean reverse, boolean swap) {
        int n = targetReg.size();
        int factor = inverse ? -1 : 1;

        IntUnaryOperator map = i -> reverse ? (n - 1 - i) : i;

        for (int i = n - 1; i >= 0; i--) {
            int qi = map.applyAsInt(i);
            this.h(targetReg.get(qi));

            for (int j = i - 1; j >= 0; j--) {
                int qj = map.applyAsInt(j);

                double theta = factor * Math.PI * Math.pow(2, j - i);
                this.cphase(theta, targetReg.get(qi), targetReg.get(qj));
            }
        }

        if (swap) {
            this.reverseQubitOrder(targetReg);
        }
    }

    /// Applies X gate to the specified qubit.
    public void x(Qubit target) {
        transformations.add(new QuantumTransformation(Gate.X, target, Math.PI));
    }

    /// Applies X gate to all qubits in the specified register.
    public void x(QuantumRegister reg) {
        reg.stream().forEach(this::x);
    }

    /// Applies X gate to all qubits in the register according to the mask.
    ///
    /// @apiNote To invert the condition, apply the bitwise NOT operator `~` to the mask.
    public void x(QuantumRegister reg, int mask) {
        for (int i = 0; i < reg.size(); i++) {
            if (isBitSet(mask, i))
                this.x(reg.get(i));
        }
    }

    /// Applies Y gate to the specified qubit.
    public void y(Qubit target) {
        transformations.add(new QuantumTransformation(Gate.Y, target, Math.PI));
    }

    /// Applies Y gate to all qubits in the specified register.
    public void y(QuantumRegister reg) {
        reg.stream().forEach(this::y);
    }

    /// Applies Y gate to all qubits in the register according to the mask.
    ///
    /// To invert the condition, apply the bitwise NOT operator `~` to the mask.
    public void y(QuantumRegister reg, int mask) {
        for (int i = 0; i < reg.size(); i++) {
            if (isBitSet(mask, i))
                this.y(reg.get(i));
        }
    }

    /// Applies Z gate to the specified qubit.
    public void z(Qubit target) {
        transformations.add(new QuantumTransformation(Gate.Z, target, Math.PI));
    }

    /// Applies Z gate to all qubits in the specified register.
    public void z(QuantumRegister reg) {
        reg.stream().forEach(this::z);
    }

    /// Applies Z gate to all qubits in the register according to the mask.
    ///
    /// To invert the condition, apply the bitwise NOT operator `~` to the mask.
    public void z(QuantumRegister reg, int mask) {
        for (int i = 0; i < reg.size(); i++) {
            if (isBitSet(mask, i))
                this.z(reg.get(i));
        }
    }

    /// Applies H gate to the specified qubit.
    public void h(Qubit target) {
        transformations.add(new QuantumTransformation(Gate.H, target));
    }

    /// Applies H gate to all qubits in the specified register.
    public void h(QuantumRegister reg) {
        reg.stream().forEach(this::h);
    }

    /// Applies PHASE gate to the specified qubit.
    ///
    /// PHASE(π) is equivalent to the Z gate.
    public void phase(double theta, Qubit target) {
        transformations.add(new QuantumTransformation(Gate.PHASE(theta), target, theta));
    }

    /// Applies PHASE gate to all qubits in the specified register.
    ///
    /// PHASE(π) is equivalent to the Z gate.
    public void phase(double theta, QuantumRegister reg) {
        reg.stream().forEach(t -> this.phase(theta, t));
    }

    /// Applies RX gate to the specified qubit.
    ///
    /// RX(π) is equivalent to the X gate (up to global phase).
    public void rx(double theta, Qubit target) {
        transformations.add(new QuantumTransformation(Gate.RX(theta), target, theta));
    }

    /// Applies RX gate to all qubits in the specified register.
    ///
    /// RX(π) is equivalent to the X gate (up to global phase).
    public void rx(double theta, QuantumRegister reg) {
        reg.stream().forEach(q -> this.rx(theta, q));
    }

    /// Applies RY gate to the specified qubit.
    ///
    /// RY(π) is equivalent to the Y gate (up to global phase).
    public void ry(double theta, Qubit target) {
        transformations.add(new QuantumTransformation(Gate.RY(theta), target, theta));
    }

    /// Applies RY gate to all qubits in the specified register.
    ///
    /// RY(π) is equivalent to the Y gate (up to global phase).
    public void ry(double theta, QuantumRegister reg) {
        reg.stream().forEach(q -> this.ry(theta, q));
    }

    /// Applies RZ gate to the specified qubit.
    ///
    /// RZ(π) is equivalent to the Z gate (up to global phase).
    public void rz(double theta, Qubit target) {
        transformations.add(new QuantumTransformation(Gate.RZ(theta), target, theta));
    }

    /// Applies RZ gate to all qubits in the specified register.
    ///
    /// RZ(π) is equivalent to the Z gate (up to global phase).
    public void rz(double theta, QuantumRegister reg) {
        reg.stream().forEach(q -> this.rz(theta, q));
    }


    /// Applies the SWAP operation using three CX (CNOT) gates.
    public void swap(Qubit targetA, Qubit targetB) {
        cx(targetA, targetB);
        cx(targetB, targetA);
        cx(targetA, targetB);
    }

    /// Applies X gate controlled on the `control` qubit to the `target` qubit.
    public void cx(Qubit control, Qubit target) {
        transformations.add(new QuantumTransformation(Gate.X, new HashSet<>(Set.of(control)), target, Math.PI));
    }

    /// Applies Y gate controlled on the `control` qubit to the `target` qubit.
    public void cy(Qubit control, Qubit target) {
        transformations.add(new QuantumTransformation(Gate.Y, new HashSet<>(Set.of(control)), target, Math.PI));
    }

    /// Applies Z gate controlled on the `control` qubit to the `target` qubit.
    public void cz(Qubit control, Qubit target) {
        transformations.add(new QuantumTransformation(Gate.Z, new HashSet<>(Set.of(control)), target, Math.PI));
    }

    /// Applies H gate controlled on the `control` qubit to the `target` qubit.
    public void ch(Qubit control, Qubit target) {
        transformations.add(new QuantumTransformation(Gate.H, new HashSet<>(Set.of(control)), target));
    }

    /// Applies PHASE gate controlled on the `control` qubit to the `target` qubit.
    public void cphase(double theta, Qubit control, Qubit target) {
        transformations.add(new QuantumTransformation(Gate.PHASE(theta), new HashSet<>(Set.of(control)), target, theta));
    }

    /// Applies X gate controlled on the `controls` qubits to the `target` qubit.
    public void mcx(List<Qubit> controls, Qubit target) {
        transformations.add(new QuantumTransformation(Gate.X, new HashSet<>(controls), target));
    }

    /// Applies PHASE gate controlled on the `controls` qubits to the `target` qubit.
    public void mcp(double theta, List<Qubit> controls, Qubit target) {
        transformations.add(new QuantumTransformation(Gate.PHASE(theta), new HashSet<>(controls), target, theta));
    }

    /// Applies a multi-controlled PHASE gate on the specified register.
    ///
    /// Because of the properties of the PHASE gate, the target qubit may be any qubit in the register,
    /// and therefore it does not need to be specified.
    ///
    /// @param theta the phase in radians
    /// @param reg   the register whose last qubit is the target and whose other qubits control it
    public void mcp(double theta, QuantumRegister reg) {
        List<Qubit> regQubitsWithoutLast = reg.getQubits().subList(0, reg.size() - 1);
        transformations.add(new QuantumTransformation(Gate.PHASE(theta), new HashSet<>(regQubitsWithoutLast), reg.getLast(), theta));
    }

    /// Classically controlled `X`, applied when the specified classical bit is true.
    ///
    /// The gate is added or skipped at the time of the call, so a bit that is set later does not
    /// change what was appended.
    ///
    /// @param controlIndex the circuit-wide index of the classical bit to test
    /// @param target       the qubit the `X` is applied to
    /// @throws ArrayIndexOutOfBoundsException if no classical bit with this index was allocated
    public void ccx(int controlIndex, Qubit target) {
        if (getClassicalBit(controlIndex))
            transformations.add(new QuantumTransformation(Gate.X, new HashSet<>(), target));
    }

    /// Classically controlled `Phase`, applied when the specified classical bit is true.
    ///
    /// The gate is added or skipped at the time of the call, so a bit that is set later does not
    /// change what was appended.
    ///
    /// @param theta   the phase in radians
    /// @param control the circuit-wide index of the classical bit to test
    /// @param target  the qubit the phase is applied to
    /// @throws ArrayIndexOutOfBoundsException if no classical bit with this index was allocated
    public void ccp(double theta, int control, Qubit target) {
        if (getClassicalBit(control))
            transformations.add(new QuantumTransformation(Gate.PHASE(theta), new HashSet<>(), target, theta));
    }

    /// Swaps qubits between two quantum registers of the same size.
    ///
    /// @apiNote Does not affect the `QuantumRegister` objects or their `Qubit` objects.
    public void swapRegisters(QuantumRegister regA, QuantumRegister regB) {

        if (regA.size() != regB.size())
            throw new IllegalArgumentException("Registers need to be the same size to swap their qubits.");

        for (int i = 0; i < regA.size(); i++)
            this.swap(regA.get(i), regB.get(i));
    }

    /// Reverses the effective order of qubits using SWAP operations.
    ///
    /// @apiNote Does not affect the `QuantumRegister` object or their `Qubit` objects.
    public void reverseQubitOrder(QuantumRegister targetReg) {
        int count = targetReg.size();
        for (int i = 0; i < count / 2; i++)
            this.swap(targetReg.get(i), targetReg.get(count - 1 - i));
    }

    /// Measures every qubit of `qReg` into the corresponding bit of `cReg` and collapses the state.
    ///
    /// The qubits are paired up by their index in the registers, so the i-th qubit of `qReg` is
    /// stored in the i-th bit of `cReg`.
    ///
    /// @param qReg the register to measure
    /// @param cReg the register the results are stored in
    /// @return the measured values, in the order of the qubits of `qReg`
    /// @throws RuntimeException if the registers differ in size
    public boolean[] measureTransformation(QuantumRegister qReg, ClassicalRegister cReg) {
        if (qReg.size() != cReg.size())
            throw new RuntimeException("Registers need to be the same size.");

        boolean[] result = new boolean[qReg.size()];
        for (int i = 0; i < qReg.size(); i++) {
            result[i] = measureTransformation(qReg.get(i), cReg.get(i));
        }

        return result;
    }

    /// Measures a qubit into the specified bit of a classical register and collapses the state.
    /// Pending quantum transformations are executed before measurement.
    public boolean measureTransformation(Qubit q, ClassicalRegister register, int bitIndex) {
        return measureTransformation(q, register.get(bitIndex));
    }

    /// Measures a qubit into a circuit-wide classical bit and collapses the state.
    /// Pending quantum transformations are executed before measurement.
    public boolean measureTransformation(Qubit q, int cIndex) {
        Integer qIndex = qubitToIndexMap.get(q);
        if (qIndex == null)
            throw new IllegalArgumentException("Cannot measure a qubit that is not part of this circuit.");
        if (cIndex < 0 || cIndex >= classicalRegisters.length)
            throw new IndexOutOfBoundsException("Classical bit index out of range: " + cIndex);

        this.run();

        boolean result = this.measureOnce(new QuantumRegister(List.of(q))) == 1;
        classicalRegisters[cIndex] = result;

        int mask = 1 << qIndex;
        int measuredValue = result ? mask : 0;
        for (int i = 0; i < state.length; i++) {
            if ((i & mask) != measuredValue)
                state[i] = Complex.ZERO;
        }

        normalizeVector(state);
        return result;
    }
}
