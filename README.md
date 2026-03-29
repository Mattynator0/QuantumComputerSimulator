# Quantum Computer Simulator by Mattynator

Simulates circuits of up to 20 qubits. 
Feel free to adjust the constant `QuantumCircuit.MAX_QUBITS` but beware that running such a circuit requires an exponential amount of contiguous memory.

You can construct quantum circuits using quantum register wrappers or through raw indexing.

### Currently supported algorithms:
- Quantum Fourier Transform
- Phase and bit oracles
- Deutsch-Jozsa
- Grover
- Quantum Phase Estimation
- Amplitude Estimation
- Polynomial evaluation
- Grover optimizer on a polynomial

Circuit examples available in `CircuitExamples.java`
