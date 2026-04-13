package org.example.simulator;

import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.example.math.Complex;
import org.example.math.ComplexMatrix;
import org.example.simulator.algorithm.MottonenStateInitialization;
import org.example.simulator.dto.CircuitAnalyticsDTO;
import org.example.simulator.dto.CircuitStateDetailsDTO;
import org.knowm.xchart.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.example.math.BigDecimalMathHelper.*;
import static org.example.math.MathUtils.*;

public class QuantumCircuit {

    @Getter
    private int qubitCount;

    @Getter
    private Complex[] state;

    private final Random random = new Random();

    public static final int MAX_QUBITS = 20;

    @Getter
    private final List<QuantumTransformation> transformations = new ArrayList<>();

    // full circuit register used solely for its indexing methods
    private QuantumRegister qubits;

    private final CircuitAnalyticsDTO analyticsDTO = new CircuitAnalyticsDTO();

    @Setter
    @Getter
    private double globalPhase = 0;

    /// ## CONSTRUCTORS

    public QuantumCircuit(final int qubitCount) {

        if (qubitCount <= 0)
            throw new IllegalArgumentException("Qubit count must be > 0");

        if (qubitCount > MAX_QUBITS)
            throw new IllegalArgumentException("Circuit is too big, qubit count is " + qubitCount);

        this.qubitCount = qubitCount;
        qubits = new QuantumRegister(qubitCount);
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
        qubits = new QuantumRegister(qubitCount);
    }

    /// ## API

    public void run() {
        this.optimizeCircuit();
        this.prepareIdentityState();

        long start = System.currentTimeMillis();
        transformations.forEach(tr -> {
            List<Integer> controls = tr.getControls();
            if (controls.isEmpty())
                this.transform(tr.getGate(), tr.getTarget());
            else if (controls.size() == 1) {
                this.cTransform(tr.getGate(), controls.getFirst(), tr.getTarget());
            } else {
                this.mcTransform(tr.getGate(), controls, tr.getTarget());
            }
        });
        long end = System.currentTimeMillis();
        analyticsDTO.executionTimeMillis = end - start;

        this.applyGlobalPhase();
    }

    @Override
    public QuantumCircuit clone() {
        QuantumCircuit copy = new QuantumCircuit(qubitCount);
        transformations.forEach(t -> copy.transformations.add(t.clone()));
        return copy;
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
        if (other.qubitCount + shift > this.qubitCount)
            throw new IllegalArgumentException("Appended quantum circuit doesn't fit on this circuit.\nthis.qubitCount = "
                    + this.qubitCount + ", other.qubitCount = "
                    + other.qubitCount + ", shift = " + shift);

        List<QuantumTransformation> otherTransformations = new ArrayList<>(other.clone().transformations);
        otherTransformations.forEach(t -> t.shiftQubits(shift));

        this.transformations.addAll(otherTransformations);
    }

    public void cAppend(int control, QuantumCircuit other, QuantumRegister reg) {
        cAppend(control, other, reg.getShift());
    }

    public void cAppend(int control, QuantumCircuit other, int shift) {
        if (other.qubitCount + shift > this.qubitCount)
            throw new IllegalArgumentException("Appended quantum circuit doesn't fit on this circuit.\nthis.qubitCount = "
                    + this.qubitCount + ", other.qubitCount = "
                    + other.qubitCount + ", shift = " + shift);

        List<QuantumTransformation> otherTransformations = new ArrayList<>(other.clone().transformations);
        otherTransformations.forEach(t -> {
            t.shiftQubits(shift);
            t.addControl(control);
        });

        this.transformations.addAll(otherTransformations);
    }

    /// ## INFORMATION

    public int[] measure(int samples) {
        // TODO make an overload with a QuantumRegister as input
        List<BigDecimal> probabilities = Arrays.stream(state)
                .map(Complex::absSquared)
                .toList();

        int[] measurements = new int[1 << qubitCount];
        for (int i = 0; i < samples; i++) {
            double rng = random.nextDouble();
            double total = 0.0;
            for (int j = 0; j < probabilities.size(); j++) {
                total += probabilities.get(j).doubleValue();
                if (total > rng) {
                    measurements[j]++;
                    break;
                }
            }
        }
        return measurements;
    }

    public int measureOnce() {
        List<BigDecimal> probabilities = Arrays.stream(state)
                .map(Complex::absSquared)
                .toList();

        double rng = random.nextDouble();
        double total = 0.0;
        for (int i = 0; i < probabilities.size(); i++) {
            total += probabilities.get(i).doubleValue();
            if (total > rng) {
                return i;
            }
        }
        return -1;
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
        return getProbabilities(IntStream.range(0, qubitCount).toArray());
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
            if (c.im.equals(BigDecimal.ZERO) && c.re.equals(BigDecimal.ZERO))
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

        analyticsDTO.transformations = transformations.size();
        analyticsDTO.controlledTransformations = (int) transformations.stream()
                .filter(t -> !t.getControls().isEmpty())
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

    private void cTransform(Gate gate, int control, int target) {

        mcTransform(gate, List.of(control), target);
    }

    private void mcTransform(Gate gate, List<Integer> controls, int target) {

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
        Complex x = state[k0];
        Complex y = state[k1];

        analyticsDTO.statevectorOperations++;
        state[k0] = x.multiply(gateMatrix.get(0, 0)).add(y.multiply(gateMatrix.get(0, 1)));
        state[k1] = x.multiply(gateMatrix.get(1, 0)).add(y.multiply(gateMatrix.get(1, 1)));
    }


    /**
     * @apiNote Calling this after {@code run()} might lead to problems due to the size of the statevector not matching the new qubit count.
     */
    void appendNewQubits(int n) {

        if (qubitCount + n > MAX_QUBITS)
            throw new IllegalArgumentException("Resulting circuit is too big; qubit count becomes " + (qubitCount + n) + ", max is " + MAX_QUBITS);

        qubitCount += n;
        qubits = new QuantumRegister(qubitCount);
    }

    private void prepareIdentityState() {
        int size = 1 << qubitCount;
        this.state = new Complex[size];

        state[0] = Complex.ONE;
        for (int i = 1; i < size; i++) {
            state[i] = Complex.ZERO;
        }
    }

    /**
     * Simple optimizer that removes identity operations.
     */
    private void optimizeCircuit() {
        for (int i = 0; i < transformations.size(); i++) {
            QuantumTransformation t = transformations.get(i);

            // TODO include controlled transformations
            if (!t.getControls().isEmpty())
                continue;

            int j = i + 1;
            while (j < transformations.size()) {
                QuantumTransformation other = transformations.get(j);

                if (t.getGate().equals(other.getGate())
                        && other.getControls().isEmpty()
                        && t.getTarget() == other.getTarget()
                        && t.getArg() == -other.getArg()) {

                    transformations.remove(j);
                    transformations.remove(i);
                    i--;
                    break;
                } else if (t.getTarget() == other.getTarget()
                        || other.getControls().contains(t.getTarget())) {
                    break;
                }

                j++;
            }
        }
    }

    private void applyGlobalPhase() {
        for (int i = 0; i < state.length; i++) {
            state[i] = state[i].multiply(Complex.cis(BigDecimal.valueOf(-globalPhase)));
        }
    }

    private QuantumCircuit remapQubits(int[] newIndices) {

        QuantumCircuit remapped = new QuantumCircuit(qubitCount);

        transformations.forEach(t -> {
            remapped.transformations.add(new QuantumTransformation(
                    t.getGate(),
                    t.getControls().stream().map(i -> newIndices[i]).collect(Collectors.toList()),
                    newIndices[t.getTarget()],
                    t.getArg()
            ));
        });

        return remapped;
    }

    /// ## STATE INITIALIZERS

    public void uniform() {
        this.h(qubits.all());
    }

    public void generateRandomState() {
        // FIXME phases are symmetric around |2^(n-1)> state which is not very random

        for (int i : qubits.all()) {
            double theta = random.nextDouble() * Math.PI;
            double phi = random.nextDouble() * 2 * Math.PI;

            this.ry(theta, i);
            this.rz(phi, i);
        }
    }

    public void geometric(double theta) {
        for (int i : qubits.all()) {
            this.phase((1 << i) * theta, i);
        }
    }

    public void geometricAlt(double theta) {
        for (int i : qubits.all()) {
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
        this.h(qubits.last());
        this.phase(Math.PI * -1, qubits.last());
        this.qft(true, false);
    }

    public void binomialApprox() {
        double theta = Math.acos(Math.sqrt(2. / 3));

        this.ry(2 * theta, qubits.last());
        this.phase(Math.PI, qubits.last());
        this.cry(Math.PI / 2, qubits.last(), 0);

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
        this.initializeWithValues(qubits, new int[]{0, frequency});
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
            throw new IllegalArgumentException("Circuit is too small for the key and value registers");

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

        this.x(qubits.all());

        this.mcp(Math.PI, qubits.allButLast(), qubits.last());

        this.x(qubits.all());
    }

    /// ## GATES

    public void x(int target) {
        transformations.add(new QuantumTransformation(Gate.X, target));
    }

    public void x(int[] targets) {
        Arrays.stream(targets).forEach(this::x);
    }

    public void x(QuantumRegister reg) {
        reg.allAsStream().forEach(this::x);
    }

    public void y(int target) {
        transformations.add(new QuantumTransformation(Gate.Y, target));
    }

    public void z(int target) {
        transformations.add(new QuantumTransformation(Gate.Z, target));
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

    public void cx(int control, int target) {
        transformations.add(new QuantumTransformation(Gate.X, new ArrayList<>(List.of(control)), target));
    }

    public void cy(int control, int target) {
        transformations.add(new QuantumTransformation(Gate.Y, new ArrayList<>(List.of(control)), target));
    }

    public void cz(int control, int target) {
        transformations.add(new QuantumTransformation(Gate.Z, new ArrayList<>(List.of(control)), target));
    }

    public void cp(double theta, int control, int target) {
        transformations.add(new QuantumTransformation(Gate.PHASE(theta), new ArrayList<>(List.of(control)), target, theta));
    }

    public void cry(double theta, int control, int target) {
        transformations.add(new QuantumTransformation(Gate.RY(theta), new ArrayList<>(List.of(control)), target, theta));
    }

    public void mcp(double theta, int[] controls, int target) {
        List<Integer> controlList = Arrays.stream(controls).boxed().collect(Collectors.toList());
        transformations.add(new QuantumTransformation(Gate.PHASE(theta), controlList, target, theta));
    }

    public void mcx(int[] controls, int target) {
        List<Integer> controlList = Arrays.stream(controls).boxed().collect(Collectors.toList());
        transformations.add(new QuantumTransformation(Gate.X, controlList, target));
    }

    public void mch(int[] controls, int target) {
        List<Integer> controlList = Arrays.stream(controls).boxed().collect(Collectors.toList());
        transformations.add(new QuantumTransformation(Gate.H, controlList, target));
    }

    public void mcry(double theta, int[] controls, int target) {
        List<Integer> controlList = Arrays.stream(controls).boxed().collect(Collectors.toList());
        transformations.add(new QuantumTransformation(Gate.RY(theta), controlList, target));
    }

    public void mcrz(double theta, int[] controls, int target) {
        List<Integer> controlList = Arrays.stream(controls).boxed().collect(Collectors.toList());
        transformations.add(new QuantumTransformation(Gate.RZ(theta), controlList, target));
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
}
