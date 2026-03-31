package org.example.simulator;

import lombok.Getter;
import lombok.val;
import org.example.math.Complex;
import org.example.math.ComplexMatrix;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.example.math.BigDecimalMathHelper.*;
import static org.example.math.MathUtils.reverseArray;

public class QuantumCircuit {

    @Getter
    private int qubitCount;

    @Getter
    private Complex[] state;

    private final Random random = new Random();

    public static final int MAX_QUBITS = 20;

    @Getter
    private final List<QuantumTransformation> transformations = new ArrayList<>();

    public QuantumCircuit(final int qubitCount) {

        if (qubitCount <= 0)
            throw new IllegalArgumentException("Qubit count must be > 0");

        if (qubitCount > MAX_QUBITS)
            throw new IllegalArgumentException("Circuit is too big, qubit count is " + qubitCount);

        this.qubitCount = qubitCount;
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
            throw new IllegalArgumentException("Circuit is too big, qubit count is " + qubitCount + ", must be <= " + MAX_QUBITS);

        this.qubitCount = qubitsBefore;
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
     * @apiNote  Calling this after `run()` will lead to errors.
     */
    void appendNewQubits(int n) {

        if (qubitCount + n > MAX_QUBITS)
            throw new IllegalArgumentException("Resulting circuit is too big; qubit count becomes " + (qubitCount + n) + ", max is " + MAX_QUBITS);

        qubitCount += n;
    }

    public void run() {
        this.optimizeCircuit();

        this.prepareIdentityState();

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
    }

    /// simple optimizer that removes identity operations
    public void optimizeCircuit() {

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
                }
                else if (t.getTarget() == other.getTarget()
                        || other.getControls().contains(t.getTarget())) {
                    break;
                }

                j++;
            }
        }
    }

    public void uniform() {
        for (int i = 0; i < this.qubitCount; i++) {
            this.h(i);
        }
    }

    public void uniform(QuantumRegister reg) {
        reg.allAsStream().forEach(this::h);
    }

    public void generateRandomState() {
        // FIXME phases are symmetric around |2^(n-1)> state which is not very random

        for (int i = 0; i < qubitCount; i++) {
            double theta = random.nextDouble() * Math.PI;
            double phi = random.nextDouble() * 2 * Math.PI;

            this.ry(theta, i);
            this.rz(phi, i);
        }
    }

    public void geometric(double theta) {
        for (int i = 0; i < qubitCount; i++) {
            this.h(i);
            this.phase((1 << i) * theta, i);
        }
    }

    public void geometricAlt(double theta) {
        for (int i = 0; i < qubitCount; i++) {
            this.h(i);
            this.phase((1 << qubitCount - 1 - i) * theta, i);
        }
    }

    // normal distribution approximation
    public void raisedCosine() {
        int lastQubit = this.qubitCount - 1;

        this.h(lastQubit);
        this.phase(Math.PI * -1, lastQubit);
        this.qft(false);
    }

    public void binomialApprox() {
        int lastQubit = this.qubitCount - 1;
        double theta = Math.acos(Math.sqrt(2. / 3));

        this.ry(2 * theta, lastQubit);
        this.phase(Math.PI, lastQubit);
        this.cry(Math.PI / 2, lastQubit, 0);

        for (int i = 1; i < qubitCount - 1; i++)
            this.cx(0, i);

        this.qft(false);
    }

    @Override
    public QuantumCircuit clone() {
        QuantumCircuit copy = new QuantumCircuit(qubitCount);
        transformations.forEach(t -> copy.appendTransformation(t.clone()));
        return copy;
    }

    public QuantumCircuit inverse() {
        QuantumCircuit inverted = new QuantumCircuit(qubitCount);

        for (int i = this.transformations.size() - 1; i >= 0; i--) {
            inverted.appendTransformation(this.transformations.get(i).inverse());
        }

        return inverted;
    }

    public void qft(boolean swap) {
        _qft(false, IntStream.range(0, qubitCount).toArray(), swap);
    }

    public void qft(int[] targets, boolean swap) {
        _qft(false, targets, swap);
    }

    public void iqft(boolean swap) {
        _qft(true, IntStream.range(0, qubitCount).toArray(), swap);
    }

    public void iqft(int[] targets, boolean swap) {
        _qft(true, targets, swap);
    }

    private void _qft(boolean inverse, int[] targets, boolean swap) {
        int factor = inverse ? -1 : 1;

        if (!swap) {
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
            this.mswap(targets);
    }

    public void encodeTerms(double coeff, int[] vars, QuantumRegister keyReg, QuantumRegister valueReg) {

        if (qubitCount < keyReg.getQubitCount() + valueReg.getQubitCount())
            throw new IllegalArgumentException("Circuit is too small for the key and value registers");

        for (int i : valueReg.all()) {
            double theta = Math.PI * coeff / (1 << i);

            if (vars.length > 1) {
                this.mcp(theta, Arrays.stream(vars).map(keyReg::get).toArray(), i);
            } else if (vars.length == 1) {
                this.cp(theta, keyReg.get(vars[0]), i);
            } else {
                this.phase(theta, i);
            }
        }
    }

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
        // TODO make an overload with a QuantumRegister as input
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
                    .append(" ".repeat(Math.max(0, maxColumnWidths[i] - columnNames[i].length())))
                    .append(" | ");
        }
        System.out.println(builder);

        for (val details : detailsList) {
            builder = new StringBuilder("| ");
            for (int i = 0; i < columnNames.length; i++) {
                builder.append(details.getString(i))
                        .append(" ".repeat(Math.max(0, maxColumnWidths[i] - details.getString(i).length())))
                        .append(" | ");
            }
            System.out.println(builder);
        }
    }

    /// Prints some important information regarding the quantum circuit.
    /// @apiNote  Some data will be missing if method is called before {@code run()}.
    /// Moreover, some data might be wrong if other circuit have been run or if other print methods have been called.
    public void printAnalytics() {

        CircuitAnalyticsDTO dto = new CircuitAnalyticsDTO();

        dto.transformations = transformations.size();
        dto.controlledOperations = (int) transformations.stream().filter(t -> !t.getControls().isEmpty()).count();
        dto.complexOperations = Complex.performedOperations;

        System.out.println(dto);
    }

    public void zeroReflection() {

        for (int i = 0; i < qubitCount; i++) {
            this.x(i);
        }

        this.mcp(Math.PI, IntStream.range(0, qubitCount - 1).toArray(), qubitCount - 1);

        for (int i = 0; i < qubitCount; i++) {
            this.x(i);
        }
    }

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

        state[k0] = x.multiply(gateMatrix.get(0, 0)).add(y.multiply(gateMatrix.get(0, 1)));
        state[k1] = x.multiply(gateMatrix.get(1, 0)).add(y.multiply(gateMatrix.get(1, 1)));
    }

    public void appendTransformation(QuantumTransformation quantumTransformation) {
        transformations.add(quantumTransformation);
    }

    public void appendAllTransformations(List<QuantumTransformation> quantumTransformations) {
        transformations.addAll(quantumTransformations);
    }

    public void append(QuantumCircuit other) {
        append(other, 0);
    }

    public void append(QuantumCircuit other, QuantumRegister reg) {
        append(other, reg.getShift());
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

    public void add(int x) {
        // TODO figure out if avoiding swaps is possible
        this.qft(true);

        double theta = x * Math.TAU / (1 << qubitCount);
        for (int i = 0; i < qubitCount; i++) {
            this.phase((1 << i) * theta, i);
        }

        this.iqft(true);
    }

    public void x(int target) {
        transformations.add(new QuantumTransformation(Gate.X, target));
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

    public void phase(double theta, int target) {
        transformations.add(new QuantumTransformation(Gate.PHASE(theta), target, theta));
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

    public void mswap(int[] targets) {
        int length = targets.length;
        for (int i = 0; i < length / 2; i++)
            this.swap(targets[i], targets[length - 1 - i]);
    }
}
