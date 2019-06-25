package ccsds123.core;

import java.io.IOException;

import com.jypec.util.bits.Bit;
import com.jypec.util.bits.BitOutputStream;
import com.jypec.util.bits.BitStreamConstants;

import ccsds123.util.Utils;

/**
 * implements 5.4.3.2 of the standard
 * 5.4.3.3 is the HYBRID version with other, more complex options
 * @author Daniel
 *
 */
public class SampleAdaptiveEntropyCoder {
	
	private int uMax;
	private int depth;
	private int gammaZero;
	private int gammaStar;
	
	private int [] accumulator;
	private int counterResetValue;
	
	public SampleAdaptiveEntropyCoder(int uMax, int depth, int bands, int gammaZero, int gammaStar, int [] accumulatorInitializationConstant) {
		if (uMax < 8 || uMax > 32)
			throw new IllegalArgumentException("Umax out of bounds!");
		this.uMax = uMax;
		if (gammaZero < 1 || gammaZero > 8) 
			throw new IllegalArgumentException("GammaZero out of bounds!");
		this.gammaZero = gammaZero;
		if (gammaStar < Math.max(4, this.gammaZero + 1) || gammaStar > 11)
			throw new IllegalArgumentException("GammaStar out of bounds!");
		this.gammaStar = gammaStar;
		
		this.depth = depth;
		this.accumulator = new int[bands];
		this.counterResetValue = 1 << this.gammaZero;
		for (int b = 0; b < accumulator.length; b++) {
			accumulator[b] = this.calcInitialAccValue(Utils.getVectorValue(accumulatorInitializationConstant, b, 0));
		}
	}
	
	
	private int getCounterValue(int t) {
		int cThresh = (1 << this.gammaStar) - (1 << this.gammaZero);
		int cOflow  = (t - ((1 << this.gammaStar) - (1 << this.gammaZero) + 1)) % (1 << (this.gammaStar - 1));
		int cValue = t <= cThresh ? (1 << this.gammaZero) - 1 + t : ((1 << (this.gammaStar - 1)) + cOflow);
				
		//sanity check
		if (cValue >= 1 << this.gammaStar)
			throw new IllegalStateException("This value is too high, something is wrong in the calculation");
		return cValue;
	}

	
	private int calcInitialAccValue(int constant) {
		int modifiedConstant;
		if (constant <= 30 - this.depth) {
			modifiedConstant = constant;
		} else {
			modifiedConstant = 2*constant + this.depth - 30;
		}
		return ((3*(1 << (modifiedConstant + 6)) - 49)*this.counterResetValue) >> 7;
	}
	
	private void lengthLimitedGolombPowerOfTwoCode(int uInt, int uIntCodeIndex,  BitOutputStream bos) throws IOException {
		int threshold = uInt >> uIntCodeIndex;
		if (threshold < this.uMax) {
			//threshold zeroes + 1 + uIntCodeIndex lsbs of uInt
			bos.writeBits(0, threshold, BitStreamConstants.ORDERING_LEFTMOST_FIRST);
			bos.writeBit(Bit.BIT_ONE);
			bos.writeBits(uInt, uIntCodeIndex, BitStreamConstants.ORDERING_LEFTMOST_FIRST);
		} else {
			//uMax zeroes + D-bit binary repr of uInt
			bos.writeBits(0, this.uMax, BitStreamConstants.ORDERING_LEFTMOST_FIRST);
			bos.writeBits(uInt, this.depth, BitStreamConstants.ORDERING_LEFTMOST_FIRST);
		}
	}
	
	public void code(int mappedQuantizerIndex, int t, int b, BitOutputStream bos) throws IOException {
		if (t == 0) {
			bos.writeBits(mappedQuantizerIndex, this.depth, BitStreamConstants.ORDERING_LEFTMOST_FIRST);
		} else {
			int uInt = mappedQuantizerIndex;
			int uIntCodeIndex;
			if (2*this.getCounterValue(t) > this.accumulator[b] + ((49*this.getCounterValue(t)) >> 7)) {
				uIntCodeIndex = 0;
			} else {
				uIntCodeIndex = 0;
				while (this.getCounterValue(t) << (uIntCodeIndex + 1) <= this.accumulator[b] + ((49*this.getCounterValue(t)) >> 7)) {
					uIntCodeIndex++;
				}
				uIntCodeIndex = Math.min(uIntCodeIndex, this.depth-2);
			}
			//code
			this.lengthLimitedGolombPowerOfTwoCode(uInt, uIntCodeIndex, bos);
			//update accumulator
			this.accumulator[b] = this.accumulator[b] + 1;
			if (this.getCounterValue(t) == (1 << this.gammaStar) - 1) {
				this.accumulator[b] >>= 1;
			}
		}
	}
	
}
