/*******************************************************************************
 * PSHDL is a library and (trans-)compiler for PSHDL input. It generates
 *     output suitable for implementation or simulation of it.
 *     
 *     Copyright (C) 2013 Karsten Becker (feedback (at) pshdl (dot) org)
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *     This License does not grant permission to use the trade names, trademarks,
 *     service marks, or product names of the Licensor, except as required for 
 *     reasonable and customary use in describing the origin of the Work.
 * 
 * Contributors:
 *     Karsten Becker - initial API and implementation
 ******************************************************************************/
package org.pshdl.interpreter.vcd;

import java.math.*;
import java.util.*;

import org.pshdl.interpreter.*;

import vcdEngine.*;
import vcdEngine.VcdTimescale.UNIT_ENUM;
import vcdEngine.VcdVariableManager.VcdVariable;

public class VCDCompareEngine {
	private final String psPrefix;
	private final String vcdPrefix;
	private final VcdFileParser vcdFile;
	private final Set<InternalInformation> matched = new HashSet<InternalInformation>();
	private final Set<InternalInformation> unmatched = new HashSet<InternalInformation>();
	private final Map<String, VcdVariable> toVar = new HashMap<String, VcdVariable>();
	private final VcdTimePeriod tp1;

	public VCDCompareEngine(String file, ExecutableModel em, String psPrefix, String vcdPrefix) {
		this.psPrefix = psPrefix;
		this.vcdPrefix = vcdPrefix;
		vcdFile = new VcdFileParser(file);
		// Reuse these objects in the hope of more performance.
		tp1 = new VcdTimePeriod();

		// Read the VCD header until the first timestamp is reached (or
		// until the end of file is reached).
		vcdFile.parseUntilNextTimestamp(tp1);
		if (tp1.getCurrentTimestamp() != VcdTimePeriod.TIMESTAMP_INITIAL_VALUES)
			throw new IllegalArgumentException("Expected initialValues");
		final VcdVariableManager varManager = vcdFile.getVarManager();
		final Set<String> fullRefs = varManager.getAllVarFullRefs();
		for (final String string : fullRefs) {
			toVar.put(string.toLowerCase(), varManager.getVarByFullRef(string));
		}
		for (final InternalInformation i : em.internals) {
			if (i.fullName.length() > psPrefix.length()) {
				final String fullName = i.fullName;
				String vcdName = getVCDName(fullName);
				if (i.actualWidth > 1) {
					vcdName += "[0]";
				}
				final VcdVariable var = toVar.get(vcdName);
				if (var != null) {
					matched.add(i);
				} else {
					unmatched.add(i);
				}
			}
		}
	}

	public String getVCDName(String fullName) {
		final String shortName = fullName.substring(psPrefix.length()).replaceAll("_", ".").toLowerCase();
		final String vcdName = vcdPrefix + shortName;
		return vcdName;
	}

	public InternalInformation[] getMatched() {
		return matched.toArray(new InternalInformation[matched.size()]);
	}

	public InternalInformation[] getUnmatched() {
		return unmatched.toArray(new InternalInformation[matched.size()]);
	}

	public void advance() {
		vcdFile.parseUntilNextTimestamp(tp1);
		System.out.println("VCDCompareEngine.advance()" + tp1.format(UNIT_ENUM.TS_NS) + " ns");
	}

	public void compareValues(IHDLInterpreter interpreter) {
		for (final InternalInformation ii : matched) {
			final BigInteger big = interpreter.getOutputBig(ii.fullName);
			final BigInteger build = getValue(ii);
			if (!big.equals(build)) {
				System.out.printf("VCDCompareEngine.compareValues()%-70s vcd: 0x%X ps: 0x%X\n", ii.fullName, build, big);
			}
		}
	}

	public BigInteger getValue(InternalInformation ii) {
		final String vcdName = getVCDName(ii.fullName);
		BigInteger build = BigInteger.ZERO;
		if (ii.actualWidth == 1) {
			if (getBit(vcdName)) {
				build = build.setBit(0);
			}
		} else {
			for (int i = 0; i < ii.actualWidth; i++) {
				final boolean bit = getBit(vcdName + '[' + i + ']');
				if (bit) {
					build = build.setBit(i);
				}
			}
		}
		return build;
	}

	public boolean getBit(String vcdName) {
		final String value = toVar.get(vcdName).getValue();
		final boolean val = "1".equals(value);
		if (!("0".equals(value) || "1".equals(value))) {
			System.out.println("VCDCompareEngine.getBit() VCD:" + vcdName + " is actually:" + value);
		}
		return val;
	}

	public void advanceTo(InternalInformation ii, BigInteger val) {
		advance();
		while (!getValue(ii).equals(val)) {
			advance();
		}
	}
}
