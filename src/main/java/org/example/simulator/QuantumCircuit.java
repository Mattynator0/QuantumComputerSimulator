package org.example.simulator;

import ch.obermuhlner.math.big.BigDecimalMath;
import lombok.Getter;
import lombok.val;
import org.example.math.Complex;
import org.example.math.ComplexMatrix;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.example.math.BigDecimalMathHelper.*;

public class QuantumCircuit {

    @Getter
    private int qubitCount;

    @Getter
    private Complex[] state;

    private final Random random = new Random();

    @Getter
    private final List<QuantumTransformation> transformations = new ArrayList<>();

    public QuantumCircuit(final int qubitCount) {
        assert qubitCount > 0;
        this.qubitCount = qubitCount;
        this.prepareIdentityState();
    }

    private void prepareIdentityState() {
        int size = (int) Math.pow(2, qubitCount);
        this.state = new Complex[size];

        state[0] = Complex.ONE;
        for (int i = 1; i < size; i++) {
            state[i] = Complex.ZERO;
        }
    }

    private void appendNewQubits(int n) {
        qubitCount += n;
        Complex[] stateCopy = this.state.clone();

        prepareIdentityState();
        System.arraycopy(stateCopy, 0, this.state, 0, stateCopy.length);
    }

    public void run() {
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

    public void uniform() {
        for (int i = 0; i < this.qubitCount; i++) {
            this.h(i);
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

    public void raisedCosine() {
        int lastQubit = this.qubitCount - 1;

        this.h(lastQubit);
        this.phase(Math.PI * -1, lastQubit);
        this.qft(true, false);
    }

    public void binomialApprox() {
        int lastQubit = this.qubitCount - 1;
        double theta = Math.acos(Math.sqrt(2. / 3));

        this.ry(2 * theta, lastQubit);
        this.phase(Math.PI, lastQubit);
        this.cry(Math.PI / 2, lastQubit, 0);

        for (int i = 1; i < qubitCount - 1; i++)
            this.cx(0, i);

        this.qft(true, false);
    }

    @Override
    public QuantumCircuit clone() {
        QuantumCircuit copy = new QuantumCircuit(qubitCount);
        transformations.forEach(t -> copy.add(t.clone()));
        return copy;
    }

    public QuantumCircuit inverse() {
        QuantumCircuit inverted = new QuantumCircuit(qubitCount);

        for (int i = this.transformations.size() - 1; i >= 0; i--) {
            inverted.add(this.transformations.get(i).inverse());
        }

        return inverted;
    }

    public void phaseOracle(int[] values) {
        for (int value : values) {
            for (int i = 0; i < qubitCount; i++) {
                if (!isBitSet(value, i)) {
                    this.x(i);
                }
            }

            this.mcp(Math.PI, IntStream.range(0, qubitCount - 1).toArray(), qubitCount - 1);

            for (int i = 0; i < qubitCount; i++) {
                if (!isBitSet(value, i)) {
                    this.x(i);
                }
            }
        }
    }

    public void bitOracle(int[] values) {

        this.appendNewQubits(1);

        for (int value : values) {
            for (int i = 0; i < qubitCount; i++) {
                if (!isBitSet(value, i)) {
                    this.x(i);
                }
            }

            this.mcx(IntStream.range(0, qubitCount - 1).toArray(), qubitCount - 1);

            for (int i = 0; i < qubitCount; i++) {
                if (!isBitSet(value, i)) {
                    this.x(i);
                }
            }
        }
    }

    public void grover(QuantumCircuit phaseOracle, int iterations) {

        assert iterations >= 0;
        assert qubitCount == phaseOracle.qubitCount;

        QuantumCircuit A = this.clone();

        for (int i = 0; i < iterations; i++) {
            this.append(phaseOracle, 0);
            this.append(A.inverse(), 0);
            this.invert_0_state();
            this.append(A, 0);
        }
    }

    public void qft(boolean reversed, boolean swap) {
        _qft(false, IntStream.range(0, qubitCount).toArray(), reversed, swap);
    }

    public void qft(int[] targets, boolean reversed, boolean swap) {
        _qft(false, targets, reversed, swap);
    }

    public void iqft(boolean reversed, boolean swap) {
        _qft(true, IntStream.range(0, qubitCount).toArray(), reversed, swap);
    }

    public void iqft(int[] targets, boolean reversed, boolean swap) {
        _qft(true, targets, reversed, swap);
    }

    private void _qft(boolean inverse, int[] targets, boolean reversed, boolean swap) {
        int factor = inverse ? -1 : 1;

        if (reversed) {
            reverseArray(targets);
        }

        for (int i = targets.length - 1; i >= 0; i--) {
            this.h(targets[i]);
            for (int j = i - 1; j >= 0; j--) {
                double theta = factor * Math.PI * Math.pow(2, (j - i));
                this.cp(theta, targets[i], targets[j]);
            }
        }

        // TODO figure out if 'swap' variable is needed or if 'NOT reversed' is equivalent for all use cases
        if (swap)
            this.mswap(targets);
    }

    public static QuantumCircuit qpe(QuantumCircuit eigenState,
                                     int estimationQubitCount,
                                     QuantumCircuit eigenCircuit,
                                     boolean swap) {

        QuantumCircuit qc = new QuantumCircuit(estimationQubitCount + eigenState.qubitCount);
        qc.append(eigenState, estimationQubitCount);

        for (int i = 0; i < estimationQubitCount; i++) {
            qc.h(i);
        }

        for (int i = 0; i < estimationQubitCount; i++) {
            for (int j = 0; j < (1 << i); j++) {
                if (swap)
                    qc.cAppend(i, eigenCircuit, estimationQubitCount);
                else
                    qc.cAppend(estimationQubitCount - i - 1, eigenCircuit, estimationQubitCount);
            }
        }

        qc.append(eigenState.inverse(), estimationQubitCount);
        qc.iqft(IntStream.range(0, estimationQubitCount).toArray(), !swap, swap);
        return qc;
    }

    public static QuantumCircuit amplitudeEstimation(QuantumCircuit initialState,
                                                     int estimationQubitCount,
                                                     QuantumCircuit phaseOracle,
                                                     int nGoodResults,
                                                     boolean swap) {

        QuantumCircuit groverCircuit = new QuantumCircuit(initialState.qubitCount);
        groverCircuit.append(initialState, 0);
        groverCircuit.grover(phaseOracle, getOptimalGroverIterations(groverCircuit.qubitCount, nGoodResults));

        return qpe(initialState, estimationQubitCount, groverCircuit, swap);
    }

    public static int getOptimalGroverIterations(int nQubits, int nGoodResults) {
        double N = 1 << nQubits;
        return (int) Math.floor(Math.PI / 4 * Math.sqrt(N / nGoodResults));
    }

    public int[] measure(int samples) {
        List<BigDecimal> probabilities = Arrays.stream(state)
                .map(Complex::absSquared)
                .toList();

        int[] measurements = new int[(int) Math.pow(2, qubitCount)];
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

    public void printProbabilities(int[] targets) {
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

        for (int i = 0; i < probs.length; i++) {
            probs[i] = zeroIfTiny(BigDecimal.valueOf(probs[i]))
                    .stripTrailingZeros()
                    .round(printMC)
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

        List<StateDetailsDTO> detailsList = new ArrayList<>();

        for (int i = 0; i < this.state.length; i++) {
            Complex c = this.state[i];
            StateDetailsDTO dto = new StateDetailsDTO();

            dto.outcome = String.valueOf(i);
            dto.binary = String.format(
                    "%" + qubitCount + "s",
                    Integer.toBinaryString(i)
            ).replace(' ', '0');

            dto.amplitude = c.toString();
            if (c.im.equals(BigDecimal.ZERO) && c.re.equals(BigDecimal.ZERO))
                dto.direction = "0.0";
            else {
                dto.direction = zeroIfTiny(BigDecimalMath.atan2(c.im, c.re, MC)
                        .multiply(BigDecimal.valueOf(180)
                                .divide(BigDecimalMath.pi(MC), MC.getPrecision(), MC.getRoundingMode()))
                        .stripTrailingZeros()
                        .round(printMC))
                        .toString();
            }
            dto.magnitude = zeroIfTiny(c.abs().stripTrailingZeros().round(printMC)).toString();
            dto.probability = zeroIfTiny(c.re.multiply(c.re, MC).add(c.im.multiply(c.im, MC), MC).stripTrailingZeros().round(printMC)).toString();

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

    private void invert_0_state() {
        for (int i = 0; i < qubitCount; i++) {
            this.x(i);
        }

        this.mcp(Math.PI, IntStream.range(0, qubitCount - 1).toArray(), qubitCount - 1);

        for (int i = 0; i < qubitCount; i++) {
            this.x(i);
        }
    }

    private void transform(Gate gate, int target) {

        int distance = (int) Math.pow(2, target);
        int prefix_count = (int) Math.pow(2, qubitCount - target - 1);

        for (int i = 0; i < distance; i++) {
            for (int j = 0; j < prefix_count; j++) {

                applyGate(i, j, distance, gate.getMatrix());
            }
        }
    }

    private void cTransform(Gate gate, int control, int target) {

        int distance = (int) Math.pow(2, target);
        int prefix_count = (int) Math.pow(2, qubitCount - target - 1);

        for (int i = 0; i < distance; i++) {
            for (int j = 0; j < prefix_count; j++) {
                if (!isBitSet(j * distance * 2 + i, control))
                    continue;

                applyGate(i, j, distance, gate.getMatrix());
            }
        }
    }

    private void mcTransform(Gate gate, List<Integer> controls, int target) {

        int distance = (int) Math.pow(2, target);
        int prefix_count = (int) Math.pow(2, qubitCount - target - 1);

        for (int i = 0; i < distance; i++) {
            for (int j = 0; j < prefix_count; j++) {

                int qubitIndex = i;
                if (!controls.stream().allMatch(control -> isBitSet(qubitIndex, control))) {
                    continue;
                }

                applyGate(i, j, distance, gate.getMatrix());
            }
        }
    }

    private void applyGate(int i, int j, int distance, ComplexMatrix gateMatrix) {
        int k0 = j * distance * 2 + i;
        int k1 = k0 + distance;

        Complex x = state[k0];
        Complex y = state[k1];

        state[k0] = x.multiply(gateMatrix.get(0, 0)).add(y.multiply(gateMatrix.get(0, 1)));
        state[k1] = x.multiply(gateMatrix.get(1, 0)).add(y.multiply(gateMatrix.get(1, 1)));
    }

    private boolean isBitSet(int num, int n) {
        return (num & (1 << n)) != 0;
    }

    private void reverseArray(int[] arr) {
        int left = 0;
        int right = arr.length - 1;

        while (left < right) {
            int temp = arr[left];
            arr[left] = arr[right];
            arr[right] = temp;

            left++;
            right--;
        }
    }

    public void add(QuantumTransformation quantumTransformation) {
        transformations.add(quantumTransformation);
    }

    public void addAll(List<QuantumTransformation> quantumTransformations) {
        transformations.addAll(quantumTransformations);
    }

    public void append(QuantumCircuit other, int shift) {
        assert this.qubitCount == other.qubitCount;
        assert shift >= 0;

        List<QuantumTransformation> otherTransformations = new ArrayList<>(other.clone().transformations);
        otherTransformations.forEach(t -> t.shiftQubits(shift));

        this.transformations.addAll(otherTransformations);
    }

    public void cAppend(int control, QuantumCircuit other, int shift) {
        assert this.qubitCount >= other.qubitCount;
        assert shift >= 0;

        List<QuantumTransformation> otherTransformations = new ArrayList<>(other.clone().transformations);
        otherTransformations.forEach(t -> {
            t.shiftQubits(shift);
            t.addControl(control);
        });

        this.transformations.addAll(otherTransformations);
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
