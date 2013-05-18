package org.pshdl.interpreter.vcd;

import java.math.*;
import java.util.*;

import org.pshdl.interpreter.*;

import vcdEngine.*;
import vcdEngine.VcdTimescale.UNIT_ENUM;
import vcdEngine.VcdVariableManager.*;

public class VCDCompareEngine {
	private final String psPrefix;
	private final String vcdPrefix;
	private final VcdFileParser vcdFile;
	private final Set<InternalInformation> matched = new HashSet<InternalInformation>();
	private final Set<InternalInformation> unmatched = new HashSet<InternalInformation>();
	private final Map<String, VcdVariable> toVar = new HashMap<String, VcdVariable>();
	private ExecutableModel em;
	private VcdTimePeriod tp1;

	public VCDCompareEngine(String file, ExecutableModel em, String psPrefix, String vcdPrefix) {
		this.psPrefix = psPrefix;
		this.vcdPrefix = vcdPrefix;
		this.em = em;
		vcdFile = new VcdFileParser(file);
		// Reuse these objects in the hope of more performance.
		tp1 = new VcdTimePeriod();

		// Read the VCD header until the first timestamp is reached (or
		// until the end of file is reached).
		vcdFile.parseUntilNextTimestamp(tp1);
		if (tp1.getCurrentTimestamp() != VcdTimePeriod.TIMESTAMP_INITIAL_VALUES)
			throw new IllegalArgumentException("Expected initialValues");
		VcdVariableManager varManager = vcdFile.getVarManager();
		Set<String> fullRefs = varManager.getAllVarFullRefs();
		for (String string : fullRefs) {
			toVar.put(string.toLowerCase(), varManager.getVarByFullRef(string));
		}
		for (InternalInformation i : em.internals) {
			if (i.fullName.length() > psPrefix.length()) {
				String fullName = i.fullName;
				String vcdName = getVCDName(fullName);
				if (i.actualWidth > 1)
					vcdName += "[0]";
				VcdVariable var = toVar.get(vcdName);
				if (var != null)
					matched.add(i);
				else
					unmatched.add(i);
			}
		}
	}

	public String getVCDName(String fullName) {
		String shortName = fullName.substring(psPrefix.length()).replaceAll("_", ".").toLowerCase();
		String vcdName = vcdPrefix + shortName;
		return vcdName;
	}

	public InternalInformation[] getMatched() {
		return (InternalInformation[]) matched.toArray(new InternalInformation[matched.size()]);
	}

	public InternalInformation[] getUnmatched() {
		return (InternalInformation[]) unmatched.toArray(new InternalInformation[matched.size()]);
	}

	public void advance() {
		vcdFile.parseUntilNextTimestamp(tp1);
		System.out.println("VCDCompareEngine.advance()" + tp1.format(UNIT_ENUM.TS_NS) + " ns");
	}

	public void compareValues(IHDLInterpreter interpreter) {
		for (InternalInformation ii : matched) {
			BigInteger big = interpreter.getOutputBig(ii.fullName);
			BigInteger build = getValue(ii);
			if (!big.equals(build))
				System.out.printf("VCDCompareEngine.compareValues()%-70s vcd: 0x%X ps: 0x%X\n", ii.fullName, build, big);
		}
	}

	public BigInteger getValue(InternalInformation ii) {
		String vcdName = getVCDName(ii.fullName);
		BigInteger build = BigInteger.ZERO;
		if (ii.actualWidth == 1) {
			if (getBit(vcdName))
				build = build.setBit(0);
		} else {
			for (int i = 0; i < ii.actualWidth; i++) {
				boolean bit = getBit(vcdName + '[' + i + ']');
				if (bit)
					build = build.setBit(i);
			}
		}
		return build;
	}

	public boolean getBit(String vcdName) {
		String value = toVar.get(vcdName).getValue();
		boolean val = "1".equals(value);
		if (!("0".equals(value) || "1".equals(value)))
			System.out.println("VCDCompareEngine.getBit() VCD:" + vcdName + " is actually:" + value);
		return val;
	}

	public void advanceTo(InternalInformation ii, BigInteger val) {
		advance();
		while (!getValue(ii).equals(val))
			advance();
	}
}
