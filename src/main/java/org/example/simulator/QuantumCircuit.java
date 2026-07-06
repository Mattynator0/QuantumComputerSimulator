package org.example.simulator;

import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.example.math.Complex;
import org.example.math.ComplexMatrix;
import org.example.math.MathUtils;
import org.example.simulator.algorithm.MottonenStateInitialization;
import org.example.simulator.dto.CircuitAnalyticsDTO;
import org.example.simulator.dto.CircuitStateDetailsDTO;
import org.example.simulator.optimizer.DAGCircuitOptimizer;
import org.example.simulator.register.ClassicalRegister;
import org.example.simulator.register.QuantumRegister;
import org.example.utils.Pair;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.example.math.BigDecimalMathHelper.PRINT_MC;
import static org.example.math.BigDecimalMathHelper.zeroIfTiny;
import static org.example.math.MathUtils.normalizeState;
import static org.example.math.MathUtils.reverseArray;

public class QuantumCircuit {

    @Getter
    private int qubitCount;

    @Getter
    private Complex[] state;

    @Getter
    private boolean[] classicalRegisters = new boolean[0];

    private final Random random = new Random();

    public static final int MAX_QUBITS = 20;

    @Setter
    @Getter
    private List<QuantumTransformation> transformations = new ArrayList<>();

    // full circuit register used solely for its indexing methods
    @Getter
    private QuantumRegister allQubits;

    private final CircuitAnalyticsDTO analyticsDTO = new CircuitAnalyticsDTO();

    @Setter
    @Getter
    private double globalPhase = 0;

    /// ## CONSTRUCTORS

    public QuantumCircuit(int qubitCount) {

        if (qubitCount <= 0)
            throw new IllegalArgumentException("Qubit count must be > 0");

        if (qubitCount > MAX_QUBITS)
            throw new IllegalArgumentException("Circuit is too big, qubit count is " + qubitCount);

        this.qubitCount = qubitCount;
        allQubits = new QuantumRegister(qubitCount);
    }

    public QuantumCircuit(QuantumRegister... registers) {

        int qubitsBefore = 0;
        for (QuantumRegister register : registers) {
            register.setShift(qubitsBefore);
            qubitsBefore += register.getQubitCount();
        }

        if (qubitsBefore <= 0)
            throw new IllegalArgumentException("Qubit count must be > 0");

        if (qubitsBefore > MAX_QUBITS)
            throw new IllegalArgumentException("Circuit is too big, qubit count is " + qubitsBefore + ", must be <= " + MAX_QUBITS);

        this.qubitCount = qubitsBefore;
        allQubits = new QuantumRegister(qubitCount);
    }

    public QuantumCircuit(QuantumCircuit other) {
        this.qubitCount = other.qubitCount;
        this.globalPhase = other.globalPhase;

        this.transformations.addAll(other.transformations
                .stream()
                .map(QuantumTransformation::new)
                .toList());

        if (other.state.length != 0) {
            this.state = new Complex[1 << qubitCount];
            for (int i = 0; i < other.state.length; i++)
                this.state[i] = new Complex(other.state[i]);
        }

        this.allQubits = new QuantumRegister(other.qubitCount);
        this.nAppliedTransformations = other.nAppliedTransformations;
        this.classicalRegisters = other.classicalRegisters;
    }

    /// ## API

    /// Runs the circuit with optimization enabled by default.
    ///
    /// @apiNote To disable optimization, pass `false` as an argument.
    public void run() {
        // FIXME optimization is disabled by default until I can make it faster than running without optimization
        this.run(false);
    }

    private int nAppliedTransformations = 0;

    public void run(boolean optimizeCircuit) {
        if (this.nAppliedTransformations == 0)
            this.prepareIdentityState();

        if (optimizeCircuit) {
            long start = System.currentTimeMillis();

            DAGCircuitOptimizer.optimize(this, nAppliedTransformations);

            long end = System.currentTimeMillis();
            analyticsDTO.optimizationTimeMillis += end - start;
        }

        long start = System.currentTimeMillis();

        for (int i = nAppliedTransformations; i < transformations.size(); i++) {
            QuantumTransformation tr = transformations.get(i);

            Set<Integer> classicalControls = tr.getClassicalControls();
            boolean ignoreTransformation = false;

            if (!classicalControls.isEmpty()) {
                for (int control : classicalControls) {
                    if (!classicalRegisters[control]) {
                        ignoreTransformation = true;
                        break;
                    }
                }
            }
            if (ignoreTransformation) continue;

            Set<Integer> quantumControls = tr.getQuantumControls();
            if (quantumControls.isEmpty())
                this.transform(tr.getGate(), tr.getTarget());
            else {
                this.mcTransform(tr.getGate(), quantumControls, tr.getTarget());
            }
        }
        nAppliedTransformations = transformations.size();

        long end = System.currentTimeMillis();
        analyticsDTO.executionTimeMillis += end - start;

        this.applyGlobalPhase();
    }

    public QuantumCircuit inverse() {
        QuantumCircuit inverted = new QuantumCircuit(qubitCount);

        for (int i = this.transformations.size() - 1; i >= 0; i--) {
            inverted.transformations.add(this.transformations.get(i).inverse());
        }

        return inverted;
    }

    public void append(QuantumCircuit other) {
        append(other, 0);
    }

    public void append(QuantumCircuit other, QuantumRegister reg) {
        append(other, reg.getShift());
    }

    public void append(QuantumCircuit other, QuantumRegister... regs) {
        IntStream merged = Stream.of(regs).flatMapToInt(QuantumRegister::allAsStream);

        QuantumCircuit remapped = other.remapQubits(merged.toArray());
        append(remapped, 0);
    }

    public void append(QuantumCircuit other, int shift) {
        cAppend(null, other, shift);
    }

    public void cAppend(int control, QuantumCircuit other, QuantumRegister reg) {
        cAppend(control, other, reg.getShift());
    }

    public void cAppend(Integer control, QuantumCircuit other, int shift) {
        if (other.qubitCount + shift > this.qubitCount)
            throw new IllegalArgumentException("Appended quantum circuit doesn't fit on this circuit.\nthis.qubitCount = "
                    + this.qubitCount + ", other.qubitCount = "
                    + other.qubitCount + ", shift = " + shift);

        List<QuantumTransformation> otherTransformations = other.transformations
                .stream()
                .map(QuantumTransformation::new)
                .toList();

        otherTransformations.forEach(t -> {
            t.shiftQubits(shift);
            if (control != null)
                t.addQuantumControl(control);
        });

        this.transformations.addAll(otherTransformations);
    }

    public void appendClassicalRegisters(ClassicalRegister... cRegs) {

        int totalLength = classicalRegisters.length;

        for (var cReg : cRegs) {
            cReg.setShift(totalLength);
            totalLength += cReg.getBitCount();
        }

        boolean[] newArray = new boolean[totalLength];
        System.arraycopy(classicalRegisters, 0, newArray, 0, classicalRegisters.length);
        classicalRegisters = newArray;
    }

    /// ## INFORMATION

    public List<Pair<String, Integer>> measure(int samples) {
        return measure(samples, allQubits);
    }

    public List<Pair<String, Integer>> measure(int samples, QuantumRegister reg) {

        double[] probabilities = this.getProbabilities(reg.all());
        Map<String, Integer> measurements = new HashMap<>();

        for (int i = 0; i < samples; i++) {
            double rng = random.nextDouble();
            double total = 0.0;
            for (int j = 0; j < probabilities.length; j++) {
                total += probabilities[j];
                if (total > rng) {
                    String m = MathUtils.toBinary(j, reg.getQubitCount());
                    if (measurements.containsKey(m))
                        measurements.put(m, measurements.get(m) + 1);
                    else
                        measurements.put(m, 1);
                    break;
                }
            }
        }

        return measurements.entrySet()
                .stream()
                .map(e -> new Pair<>(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing((Pair<String, Integer> p) -> p.second()).reversed())
                .collect(Collectors.toList());
    }

    public int measureOnce() {
        return this.measureOnce(allQubits);
    }

    public int measureOnce(QuantumRegister reg) {
        double[] probabilities = this.getProbabilities(reg.all());

        double rng = random.nextDouble();
        double total = 0.0;
        for (int i = 0; i < probabilities.length; i++) {
            total += probabilities[i];
            if (total > rng) {
                return i;
            }
        }
        return -1;
    }

    public double[] getProbabilities() {
        return getProbabilities(allQubits.all());
    }

    public double[] getProbabilities(int[] targets) {
        int numTargets = targets.length;
        int numOutcomes = 1 << numTargets;
        double[] probs = new double[numOutcomes];

        for (int i = 0; i < state.length; i++) {
            // probability of this basis state
            double p = state[i].absSquared().doubleValue();

            // extract target bits and build outcome index
            int outcome = 0;
            for (int t = 0; t < numTargets; t++) {

                // get target index
                int target = targets[t];

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

    public void printProbabilities(int[] targets) {

        double[] probs = getProbabilities(targets);

        for (int i = 0; i < probs.length; i++) {
            probs[i] = zeroIfTiny(BigDecimal.valueOf(probs[i]))
                    .stripTrailingZeros()
                    .round(PRINT_MC)
                    .doubleValue();

            System.out.print(i + ": " + probs[i] + "\n");
        }
    }

    public void printState() {
        for (int i = 0; i < this.state.length; i++) {
            System.out.print(i + ": " + this.state[i] + "\n");
        }
    }

    public void printStateDetailed() {
        printStateDetailed(false);
    }

    public void printStateDetailed(boolean nonZeroOnly) {
        String[] columnNames = {"Outcome", "Binary", "Amplitude", "Direction", "Magnitude", "Probability"};

        List<CircuitStateDetailsDTO> detailsList = new ArrayList<>();

        for (int i = 0; i < this.state.length; i++) {
            Complex c = this.state[i];
            CircuitStateDetailsDTO dto = new CircuitStateDetailsDTO();

            dto.outcome = String.valueOf(i);
            dto.binary = String.format(
                    "%" + qubitCount + "s",
                    Integer.toBinaryString(i)
            ).replace(' ', '0');

            dto.amplitude = c.toString();
            if (c.im().equals(BigDecimal.ZERO) && c.re().equals(BigDecimal.ZERO))
                dto.direction = "0.0";
            else {
                dto.direction = zeroIfTiny(c.direction()
                        .stripTrailingZeros()
                        .round(PRINT_MC))
                        .toString();
            }
            dto.magnitude = zeroIfTiny(c.abs().stripTrailingZeros().round(PRINT_MC)).toString();
            dto.probability = zeroIfTiny(c.absSquared().stripTrailingZeros().round(PRINT_MC)).toString();

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

    /// Prints analytics about the quantum circuit.
    ///
    /// @apiNote Some data (e.g. execution time) will be missing if called before {@code run()}.
    public void printAnalytics() {

        analyticsDTO.qubitCount = qubitCount;
        analyticsDTO.transformations = transformations.size();
        analyticsDTO.controlledTransformations = (int) transformations.stream()
                .filter(t -> !t.getQuantumControls().isEmpty())
                .count();

        System.out.println(analyticsDTO);
    }

    /// Shows a histogram of statevector probabilities
    public void displayProbabilityChart(int[] targets) {
        // FIXME indexing starts at 1 instead of 0
        // TODO make this prettier and more informative
        XYChart chart = new XYChartBuilder().build();
        XYSeries series = chart.addSeries("probability", this.getProbabilities(targets));
        series.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);

        new SwingWrapper<>(chart).displayChart();
    }

    /// ## UTILITY

    private void transform(Gate gate, int target) {

        int targetMask = 1 << target;

        for (int i = 0; i < (1 << qubitCount) - 1; i++) {
            if ((i & targetMask) != 0)
                continue;

            applyGate(i, i | targetMask, gate.getMatrix());
        }
    }

    private void mcTransform(Gate gate, Set<Integer> controls, int target) {

        int targetMask = 1 << target;
        int controlMask = 0;
        for (int control : controls) {
            controlMask |= (1 << control);
        }

        for (int i = 0; i < (1 << qubitCount) - 1; i++) {
            if ((i & targetMask) != 0)
                continue;

            if ((i & controlMask) != controlMask)
                continue;

            applyGate(i, i | targetMask, gate.getMatrix());
        }
    }

    private void applyGate(int k0, int k1, ComplexMatrix gateMatrix) {
        analyticsDTO.statevectorOperations++;

        Complex c0 = state[k0];
        Complex c1 = state[k1];

        state[k0] = c0.multiply(gateMatrix.get(0, 0)).add(c1.multiply(gateMatrix.get(0, 1)));
        state[k1] = c0.multiply(gateMatrix.get(1, 0)).add(c1.multiply(gateMatrix.get(1, 1)));
    }

    /**
     * @apiNote Calling this after {@code run()} might lead to problems due to the size of the statevector not matching the new qubit count.
     */
    void appendNewQubits(int n) {

        if (qubitCount + n > MAX_QUBITS)
            throw new IllegalArgumentException("Resulting circuit is too big; qubit count becomes " + (qubitCount + n) + ", max is " + MAX_QUBITS);

        qubitCount += n;
        allQubits = new QuantumRegister(qubitCount);
    }

    private void prepareIdentityState() {
        int size = 1 << qubitCount;
        this.state = new Complex[size];

        state[0] = Complex.ONE;
        for (int i = 1; i < size; i++) {
            state[i] = Complex.ZERO;
        }
    }

    private void applyGlobalPhase() {
        for (int i = 0; i < state.length; i++) {
            state[i] = state[i].multiply(Complex.cis(BigDecimal.valueOf(-globalPhase)));
        }
        globalPhase = 0;
    }

    private QuantumCircuit remapQubits(int[] newIndices) {

        QuantumCircuit remapped = new QuantumCircuit(qubitCount);

        transformations.forEach(t -> remapped.transformations
                .add(new QuantumTransformation(
                        t.getGate(),
                        t.getQuantumControls().stream().map(i -> newIndices[i]).collect(Collectors.toSet()),
                        newIndices[t.getTarget()],
                        t.getArg()
                )));

        return remapped;
    }

    /// ## STATE INITIALIZERS

    public void uniform() {
        this.h(allQubits.all());
    }

    public void generateRandomState() {
        // FIXME phases are symmetric around |2^(n-1)> state which is not very random

        for (int i : allQubits.all()) {
            double theta = random.nextDouble() * Math.PI;
            double phi = random.nextDouble() * 2 * Math.PI;

            this.ry(theta, i);
            this.rz(phi, i);
        }
    }

    public void geometric(double theta) {
        for (int i : allQubits.all()) {
            this.phase((1 << i) * theta, i);
        }
    }

    public void geometricAlt(double theta) {
        for (int i : allQubits.all()) {
            this.phase((1 << qubitCount - 1 - i) * theta, i);
        }
    }

    public void geometricAlt(double theta, int[] targets) {
        for (int i = 0; i < targets.length; i++) {
            this.phase((1 << targets.length - 1 - i) * theta, targets[i]);
        }
    }

    /// normal distribution approximation
    public void raisedCosine() {
        this.h(allQubits.last());
        this.phase(Math.PI * -1, allQubits.last());
        this.qft(true, false);
    }

    public void binomialApprox() {
        double theta = Math.acos(Math.sqrt(2. / 3));

        this.ry(2 * theta, allQubits.last());
        this.phase(Math.PI, allQubits.last());
        this.cry(Math.PI / 2, allQubits.last(), 0);

        for (int i = 1; i < qubitCount - 1; i++)
            this.cx(0, i);

        this.qft(true, false);
    }

    /**
     * Creates a state with probabilities equal to cos^2(k * pi * freq/2)
     *
     * @param frequency frequency of the squared cosine wave
     */
    public void squaredCosine(int frequency) {
        this.initializeWithValues(allQubits, new int[]{0, frequency});
        this.qft(false, true);
    }

    /// Prepares the specified state using CNOTs and RY, RZ gates. Expected to run on the identity statevector.
    public void initializeWithValues(QuantumRegister reg, int[] values) {
        Complex[] state = new Complex[1 << reg.getQubitCount()];

        Arrays.fill(state, Complex.ZERO);
        for (int v : values) {
            state[v] = Complex.ONE;
        }

        MottonenStateInitialization.perform(this, reg, state);
    }

    /// Prepares the specified state using CNOTs and RY, RZ gates.
    ///
    /// Expected to run as the first operation on the circuit (i.e. requires the statevector to be in the |0> state).
    public void initializeWithState(QuantumRegister reg, Complex[] state) {
        MottonenStateInitialization.perform(this, reg, state);
    }

    /// ## ALGORITHMS

    public void qft(boolean reverse, boolean swap) {
        _qft(false, IntStream.range(0, qubitCount).toArray(), reverse, swap);
    }

    public void qft(int[] targets, boolean reverse, boolean swap) {
        _qft(false, targets, reverse, swap);
    }

    public void iqft(boolean reverse, boolean swap) {
        _qft(true, IntStream.range(0, qubitCount).toArray(), reverse, swap);
    }

    public void iqft(int[] targets, boolean reverse, boolean swap) {
        _qft(true, targets, reverse, swap);
    }

    private void _qft(boolean inverse, int[] targets, boolean reverse, boolean swap) {
        int factor = inverse ? -1 : 1;

        if (reverse) {
            reverseArray(targets);
        }

        for (int i = targets.length - 1; i >= 0; i--) {
            this.h(targets[i]);
            for (int j = i - 1; j >= 0; j--) {
                double theta = factor * Math.PI * Math.pow(2, (j - i));
                this.cp(theta, targets[i], targets[j]);
            }
        }

        if (swap)
            this.reverseWithSwaps(targets);

        if (reverse) {
            reverseArray(targets); // undo reversing
        }
    }

    public void encodeTerms(double coeff, int[] vars, QuantumRegister keyReg, QuantumRegister valueReg) {

        if (qubitCount < keyReg.getQubitCount() + valueReg.getQubitCount())
            throw new IllegalArgumentException("Circuit is too small for the first and second registers");

        for (int i : valueReg.all()) {
            double theta = Math.PI * coeff / (1 << i);

            if (vars.length > 1) {
                this.mcp(theta, keyReg.get(vars), i);
            } else if (vars.length == 1) {
                this.cp(theta, keyReg.get(vars[0]), i);
            } else {
                this.phase(theta, i);
            }
        }
    }

    public void zeroReflection() {

        this.x(allQubits.all());

        this.mcp(Math.PI, allQubits.allButLast(), allQubits.last());

        this.x(allQubits.all());
    }

    /// Adds `x` in Fourier basis controlled on `controls`.
    public void mcFourierAdd(int[] controls, QuantumRegister reg, int x, boolean isSwapped) {
        double theta = Math.TAU / (1 << reg.getQubitCount());

        if (isSwapped)
            for (int i = 0; i < reg.getQubitCount(); i++) {
                this.mcp((1 << i) * x * theta, controls, reg.get(i));
            }
        else
            for (int i = 0; i < reg.getQubitCount(); i++) {
                this.mcp((1 << reg.getQubitCount() - 1 - i) * x * theta, controls, reg.get(i));
            }
    }

    /// ## GATES

    public void x(int target) {
        transformations.add(new QuantumTransformation(Gate.X, target, Math.PI));
    }

    public void x(int[] targets) {
        Arrays.stream(targets).forEach(this::x);
    }

    public void x(QuantumRegister reg) {
        reg.allAsStream().forEach(this::x);
    }

    public void y(int target) {
        transformations.add(new QuantumTransformation(Gate.Y, target, Math.PI));
    }

    public void z(int target) {
        transformations.add(new QuantumTransformation(Gate.Z, target, Math.PI));
    }

    public void h(int target) {
        transformations.add(new QuantumTransformation(Gate.H, target));
    }

    public void h(int[] targets) {
        Arrays.stream(targets).forEach(this::h);
    }

    public void h(QuantumRegister reg) {
        reg.allAsStream().forEach(this::h);
    }

    public void phase(double theta, int target) {
        transformations.add(new QuantumTransformation(Gate.PHASE(theta), target, theta));
    }

    public void phase(double theta, int[] targets) {
        Arrays.stream(targets).forEach(t -> this.phase(theta, t));
    }

    public void phase(double theta, QuantumRegister reg) {
        reg.allAsStream().forEach(t -> this.phase(theta, t));
    }

    public void rx(double theta, int target) {
        transformations.add(new QuantumTransformation(Gate.RX(theta), target, theta));
    }

    public void ry(double theta, int target) {
        transformations.add(new QuantumTransformation(Gate.RY(theta), target, theta));
    }

    public void rz(double theta, int target) {
        transformations.add(new QuantumTransformation(Gate.RZ(theta), target, theta));
    }

    public void swap(int targetA, int targetB) {
        cx(targetA, targetB);
        cx(targetB, targetA);
        cx(targetA, targetB);
    }

    /// c - (quantum) controlled
    /// mc - (quantum) multi-controlled
    /// cc - classically controlled

    public void cx(int control, int target) {
        transformations.add(new QuantumTransformation(Gate.X, new HashSet<>(List.of(control)), target, Math.PI));
    }

    public void cy(int control, int target) {
        transformations.add(new QuantumTransformation(Gate.Y, new HashSet<>(List.of(control)), target, Math.PI));
    }

    public void cz(int control, int target) {
        transformations.add(new QuantumTransformation(Gate.Z, new HashSet<>(List.of(control)), target, Math.PI));
    }

    public void cp(double theta, int control, int target) {
        transformations.add(new QuantumTransformation(Gate.PHASE(theta), new HashSet<>(List.of(control)), target, theta));
    }

    public void cry(double theta, int control, int target) {
        transformations.add(new QuantumTransformation(Gate.RY(theta), new HashSet<>(List.of(control)), target, theta));
    }

    public void mcp(double theta, int[] controls, int target) {
        Set<Integer> controlSet = Arrays.stream(controls).boxed().collect(Collectors.toSet());
        transformations.add(new QuantumTransformation(Gate.PHASE(theta), controlSet, target, theta));
    }

    public void mcx(int[] controls, int target) {
        Set<Integer> controlSet = Arrays.stream(controls).boxed().collect(Collectors.toSet());
        transformations.add(new QuantumTransformation(Gate.X, controlSet, target));
    }

    public void mch(int[] controls, int target) {
        Set<Integer> controlSet = Arrays.stream(controls).boxed().collect(Collectors.toSet());
        transformations.add(new QuantumTransformation(Gate.H, controlSet, target));
    }

    public void mcry(double theta, int[] controls, int target) {
        Set<Integer> controlSet = Arrays.stream(controls).boxed().collect(Collectors.toSet());
        transformations.add(new QuantumTransformation(Gate.RY(theta), controlSet, target));
    }

    public void mcrz(double theta, int[] controls, int target) {
        Set<Integer> controlSet = Arrays.stream(controls).boxed().collect(Collectors.toSet());
        transformations.add(new QuantumTransformation(Gate.RZ(theta), controlSet, target));
    }

    /// Swaps qubits between listA and listB
    public void mswap(int[] listA, int[] listB) {
        for (int i = 0; i < listA.length; i++)
            this.swap(listA[i], listB[i]);
    }

    public void reverseWithSwaps(int[] targets) {
        int length = targets.length;
        for (int i = 0; i < length / 2; i++)
            this.swap(targets[i], targets[length - 1 - i]);
    }

    public void measureTransformation(int qIndex, int cIndex) {
        this.run();
        double[] probs = this.getProbabilities(new int[]{qIndex});

        boolean result = random.nextDouble() > probs[0];
        classicalRegisters[cIndex] = result;

        int mask = 1 << qIndex;
        int r = result ? 1 << qIndex : 0;
        for (int i = 0; i < state.length; i++) {
            if ((i & mask) != r)
                state[i] = Complex.ZERO;
        }

        normalizeState(state);
    }

    /// Classically controlled `X`.
    public void ccx(int control, int target) {
        transformations.add(new QuantumTransformation(Gate.X, new HashSet<>(), new HashSet<>(List.of(control)), target));
    }

    /// Classically controlled `Phase`.
    public void ccp(double theta, int control, int target) {
        transformations.add(new QuantumTransformation(Gate.PHASE(theta), new HashSet<>(), new HashSet<>(List.of(control)), target, theta));
    }
}
