package org.ripple.power;

import org.ripple.bouncycastle.asn1.ASN1InputStream;
import org.ripple.bouncycastle.asn1.DERInteger;
import org.ripple.bouncycastle.asn1.DERSequenceGenerator;
import org.ripple.bouncycastle.asn1.DLSequence;
import org.ripple.bouncycastle.asn1.sec.SECNamedCurves;
import org.ripple.bouncycastle.asn1.x9.X9ECParameters;
import org.ripple.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.ripple.bouncycastle.crypto.engines.AESEngine;
import org.ripple.bouncycastle.crypto.generators.SCrypt;
import org.ripple.bouncycastle.crypto.params.ECDomainParameters;
import org.ripple.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.ripple.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.ripple.bouncycastle.crypto.params.KeyParameter;
import org.ripple.bouncycastle.crypto.params.ParametersWithRandom;
import org.ripple.bouncycastle.crypto.signers.ECDSASigner;
import org.ripple.bouncycastle.math.ec.ECPoint;
import org.ripple.bouncycastle.util.Arrays;
import org.ripple.power.utils.KeyPair;



















import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class CoinUtils {
	private static final ECDomainParameters EC_PARAMS;
	// only Bitcoin base58
	private static final char[] BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
			.toCharArray();
	public static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final BigInteger LARGEST_PRIVATE_KEY = new BigInteger(
			"FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
			16);
	public static final long MIN_FEE_PER_KB = 10000;

	static {
		X9ECParameters params = SECNamedCurves.getByName("secp256k1");
		EC_PARAMS = new ECDomainParameters(params.getCurve(), params.getG(),
				params.getN(), params.getH());
	}

	public static byte[] generatePublicKey(BigInteger privateKey,
			boolean compressed) {
		synchronized (EC_PARAMS) {
			ECPoint res = EC_PARAMS.getG().multiply(privateKey);
			return res.getEncoded(compressed);
		}
	}

	public static byte[] generateKey(BigInteger privateKey) {
		synchronized (EC_PARAMS) {
			ECPoint res = EC_PARAMS.getG().multiply(privateKey);
			return res.getEncoded(true);
		}
	}

	public static byte[] doubleSha256(byte[] bytes) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			return sha256.digest(sha256.digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public static String formatValue(double value) {
		if (value < 0) {
			throw new NumberFormatException("Negative value " + value);
		}
		String s = String.format("%.8f", value);
		while (s.length() > 1 && (s.endsWith("0") || s.endsWith("."))) {
			s = (s.substring(0, s.length() - 1));
		}
		return s;
	}

	public static String formatValue(long value) throws NumberFormatException {
		if (value < 0) {
			throw new NumberFormatException("Negative value " + value);
		}
		StringBuilder sb = new StringBuilder(Long.toString(value));
		while (sb.length() <= 8) {
			sb.insert(0, '0');
		}
		sb.insert(sb.length() - 8, '.');
		while (sb.length() > 1
				&& (sb.charAt(sb.length() - 1) == '0' || sb
						.charAt(sb.length() - 1) == '.')) {
			sb.setLength(sb.length() - 1);
		}
		return sb.toString();
	}

	public static long parseValue(String valueStr) throws NumberFormatException {
		return (long) (Double.parseDouble(valueStr) * 1e8);
	}

	public static class PrivateKeyInfo {
		public static final int TYPE_WIF = 0;
		public static final int TYPE_MINI = 1;
		public static final int TYPE_BRAIN_WALLET = 2;
		public final int type;
		public final String privateKeyEncoded;
		public final BigInteger privateKeyDecoded;
		public final boolean isPublicKeyCompressed;

		public PrivateKeyInfo(int type, String privateKeyEncoded,
				BigInteger privateKeyDecoded, boolean isPublicKeyCompressed) {
			this.type = type;
			this.privateKeyEncoded = privateKeyEncoded;
			this.privateKeyDecoded = privateKeyDecoded;
			this.isPublicKeyCompressed = isPublicKeyCompressed;
		}
	}

	public static class Bip38PrivateKeyInfo extends PrivateKeyInfo {
		public static final int TYPE_BIP38 = 4;

		public final String confirmationCode;
		public final String password;

		public Bip38PrivateKeyInfo(String privateKeyEncoded,
				String confirmationCode, boolean isPublicKeyCompressed) {
			super(TYPE_BIP38, privateKeyEncoded, null, isPublicKeyCompressed);
			this.confirmationCode = confirmationCode;
			this.password = null;
		}

		public Bip38PrivateKeyInfo(String privateKeyEncoded,
				BigInteger privateKeyDecoded, String password,
				boolean isPublicKeyCompressed) {
			super(TYPE_BIP38, privateKeyEncoded, privateKeyDecoded,
					isPublicKeyCompressed);
			this.confirmationCode = null;
			this.password = password;
		}
	}

	public static PrivateKeyInfo decodePrivateKey(String encodedPrivateKey) {
		if (encodedPrivateKey.length() > 0) {
			try {
				byte[] decoded = decodeBase58(encodedPrivateKey);
				if (decoded != null
						&& (decoded.length == 37 || decoded.length == 38)
						&& (decoded[0] & 0xff) == 0x80) {
					if (verifyChecksum(decoded)) {
						byte[] secret = new byte[32];
						System.arraycopy(decoded, 1, secret, 0, secret.length);
						boolean isPublicKeyCompressed;
						if (decoded.length == 38) {
							if (decoded[decoded.length - 5] == 1) {
								isPublicKeyCompressed = true;
							} else {
								return null;
							}
						} else {
							isPublicKeyCompressed = false;
						}
						BigInteger privateKeyBigInteger = new BigInteger(1,
								secret);
						if (privateKeyBigInteger.compareTo(BigInteger.ONE) > 0
								&& privateKeyBigInteger
										.compareTo(LARGEST_PRIVATE_KEY) < 0) {
							return new PrivateKeyInfo(PrivateKeyInfo.TYPE_WIF,
									encodedPrivateKey, privateKeyBigInteger,
									isPublicKeyCompressed);
						}
					}
				} else if (decoded != null
						&& decoded.length == 43
						&& (decoded[0] & 0xff) == 0x01
						&& ((decoded[1] & 0xff) == 0x43 || (decoded[1] & 0xff) == 0x42)) {
					if (verifyChecksum(decoded)) {
						return new PrivateKeyInfo(
								Bip38PrivateKeyInfo.TYPE_BIP38,
								encodedPrivateKey, null, false);
					}
				}
			} catch (Exception ignored) {
			}
		}
		return decodePrivateKeyAsSHA256(encodedPrivateKey);
	}

	public static PrivateKeyInfo decodePrivateKeyAsSHA256(
			String encodedPrivateKey) {
		if (encodedPrivateKey.length() > 0) {
			try {
				MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
				BigInteger privateKeyBigInteger = new BigInteger(1,
						sha256.digest(encodedPrivateKey.getBytes()));
				if (privateKeyBigInteger.compareTo(BigInteger.ONE) > 0
						&& privateKeyBigInteger.compareTo(LARGEST_PRIVATE_KEY) < 0) {
					int type;

					if (sha256.digest((encodedPrivateKey + '?')
							.getBytes("UTF-8"))[0] == 0) {
						type = PrivateKeyInfo.TYPE_MINI;
					} else {
						type = PrivateKeyInfo.TYPE_BRAIN_WALLET;
					}
					final boolean isPublicKeyCompressed = false;
					return new PrivateKeyInfo(type, encodedPrivateKey,
							privateKeyBigInteger, isPublicKeyCompressed);
				}
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	public static boolean verifyBitcoinAddress(String address) {
		byte[] decodedAddress = decodeBase58(address);
		return !(decodedAddress == null || decodedAddress.length < 6
				|| decodedAddress[0] != 0 || !verifyChecksum(decodedAddress));
	}

	public static boolean verifyChecksum(byte[] bytesWithChecksumm) {
		try {
			if (bytesWithChecksumm == null || bytesWithChecksumm.length < 5) {
				return false;
			}
			MessageDigest digestSha = MessageDigest.getInstance("SHA-256");
			digestSha.update(bytesWithChecksumm, 0,
					bytesWithChecksumm.length - 4);
			byte[] first = digestSha.digest();
			byte[] calculatedDigest = digestSha.digest(first);
			boolean checksumValid = true;
			for (int i = 0; i < 4; i++) {
				if (calculatedDigest[i] != bytesWithChecksumm[bytesWithChecksumm.length
						- 4 + i]) {
					checksumValid = false;
				}
			}
			return checksumValid;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static byte[] sha256ripemd160(byte[] publicKey) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			byte[] sha256hash = sha256.digest(publicKey);
			RIPEMD160Digest ripemd160Digest = new RIPEMD160Digest();
			ripemd160Digest.update(sha256hash, 0, sha256hash.length);
			byte[] hashedPublicKey = new byte[20];
			ripemd160Digest.doFinal(hashedPublicKey, 0);
			return hashedPublicKey;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String publicKeyToAddress(byte[] publicKey) {
		try {
			byte[] hashedPublicKey = sha256ripemd160(publicKey);
			int size = hashedPublicKey.length;
			byte[] addressBytes = new byte[1 + size + 4];
			addressBytes[0] = 0;
			System.arraycopy(hashedPublicKey, 0, addressBytes, 1, size);
			MessageDigest digestSha = MessageDigest.getInstance("SHA-256");
			digestSha.update(addressBytes, 0, addressBytes.length - 4);
			byte[] check = digestSha.digest(digestSha.digest());
			System.arraycopy(check, 0, addressBytes,
					hashedPublicKey.length + 1, 4);
			return CoinUtils.encodeBase58(addressBytes);
		} catch (Exception e) {
			return "";
		}
	}

	private static final int BASE58_CHUNK_DIGITS = 10;
	private static final BigInteger BASE58_CHUNK_MOD = BigInteger
			.valueOf(0x5fa8624c7fba400L);
	private static final byte[] BASE58_VALUES = new byte[] { -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -2, -2, -2, -2, -2, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -2, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7,
			8, -1, -1, -1, -1, -1, -1, -1, 9, 10, 11, 12, 13, 14, 15, 16, -1,
			17, 18, 19, 20, 21, -1, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
			-1, -1, -1, -1, -1, -1, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43,
			-1, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 };

	public static byte[] decodeBase58(String input) {
		if (input == null) {
			return null;
		}
		input = input.trim();
		if (input.length() == 0) {
			return new byte[0];
		}
		BigInteger resultNum = BigInteger.ZERO;
		int nLeadingZeros = 0;
		while (nLeadingZeros < input.length()
				&& input.charAt(nLeadingZeros) == BASE58[0]) {
			nLeadingZeros++;
		}
		long acc = 0;
		int nDigits = 0;
		int p = nLeadingZeros;
		while (p < input.length()) {
			int v = BASE58_VALUES[input.charAt(p) & 0xff];
			if (v >= 0) {
				acc *= 58;
				acc += v;
				nDigits++;
				if (nDigits == BASE58_CHUNK_DIGITS) {
					resultNum = resultNum.multiply(BASE58_CHUNK_MOD).add(
							BigInteger.valueOf(acc));
					acc = 0;
					nDigits = 0;
				}
				p++;
			} else {
				break;
			}
		}
		if (nDigits > 0) {
			long mul = 58;
			while (--nDigits > 0) {
				mul *= 58;
			}
			resultNum = resultNum.multiply(BigInteger.valueOf(mul)).add(
					BigInteger.valueOf(acc));
		}
		final int BASE58_SPACE = -2;
		while (p < input.length()
				&& BASE58_VALUES[input.charAt(p) & 0xff] == BASE58_SPACE) {
			p++;
		}
		if (p < input.length()) {
			return null;
		}
		byte[] plainNumber = resultNum.toByteArray();
		int plainNumbersOffs = plainNumber[0] == 0 ? 1 : 0;
		byte[] result = new byte[nLeadingZeros + plainNumber.length
				- plainNumbersOffs];
		System.arraycopy(plainNumber, plainNumbersOffs, result, nLeadingZeros,
				plainNumber.length - plainNumbersOffs);
		return result;
	}

	public static String encodeBase58(byte[] input) {
		if (input == null) {
			return null;
		}
		StringBuilder str = new StringBuilder((input.length * 350) / 256 + 1);
		BigInteger bn = new BigInteger(1, input);
		long rem;
		while (true) {
			BigInteger[] divideAndRemainder = bn
					.divideAndRemainder(BASE58_CHUNK_MOD);
			bn = divideAndRemainder[0];
			rem = divideAndRemainder[1].longValue();
			if (bn.compareTo(BigInteger.ZERO) == 0) {
				break;
			}
			for (int i = 0; i < BASE58_CHUNK_DIGITS; i++) {
				str.append(BASE58[(int) (rem % 58)]);
				rem /= 58;
			}
		}
		while (rem != 0) {
			str.append(BASE58[(int) (rem % 58)]);
			rem /= 58;
		}
		str.reverse();
		int nLeadingZeros = 0;
		while (nLeadingZeros < input.length && input[nLeadingZeros] == 0) {
			str.insert(0, BASE58[0]);
			nLeadingZeros++;
		}
		return str.toString();
	}

	final static char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	public static String toHex(byte[] bytes) {
		if (bytes == null) {
			return "";
		}
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for (int j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static byte[] fromHex(String s) {
		if (s != null) {
			try {
				StringBuilder sb = new StringBuilder(s.length());
				for (int i = 0; i < s.length(); i++) {
					char ch = s.charAt(i);
					if (!Character.isWhitespace(ch)) {
						sb.append(ch);
					}
				}
				s = sb.toString();
				int len = s.length();
				byte[] data = new byte[len / 2];
				for (int i = 0; i < len; i += 2) {
					int hi = (Character.digit(s.charAt(i), 16) << 4);
					int low = Character.digit(s.charAt(i + 1), 16);
					if (hi >= 256 || low < 0 || low >= 16) {
						return null;
					}
					data[i / 2] = (byte) (hi | low);
				}
				return data;
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	public static byte[] sign(BigInteger privateKey, byte[] input) {
		synchronized (EC_PARAMS) {
			ECDSASigner signer = new ECDSASigner();
			ECPrivateKeyParameters privateKeyParam = new ECPrivateKeyParameters(
					privateKey, EC_PARAMS);
			signer.init(true, new ParametersWithRandom(privateKeyParam,
					SECURE_RANDOM));
			BigInteger[] sign = signer.generateSignature(input);
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream(72);
				DERSequenceGenerator derGen = new DERSequenceGenerator(baos);
				derGen.addObject(new DERInteger(sign[0]));
				derGen.addObject(new DERInteger(sign[1]));
				derGen.close();
				return baos.toByteArray();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static boolean verify(byte[] publicKey, byte[] signature, byte[] msg) {
		synchronized (EC_PARAMS) {
			boolean valid;
			ECDSASigner signerVer = new ECDSASigner();
			try {
				ECPublicKeyParameters pubKey = new ECPublicKeyParameters(
						EC_PARAMS.getCurve().decodePoint(publicKey), EC_PARAMS);
				signerVer.init(false, pubKey);
				ASN1InputStream derSigStream = new ASN1InputStream(signature);
				DLSequence seq = (DLSequence) derSigStream.readObject();
				BigInteger r = ((DERInteger) seq.getObjectAt(0))
						.getPositiveValue();
				BigInteger s = ((DERInteger) seq.getObjectAt(1))
						.getPositiveValue();
				derSigStream.close();
				valid = signerVer.verifySignature(msg, r, s);
			} catch (Exception e) {
				throw new RuntimeException();
			}
			return valid;
		}
	}

	public static byte[] reverse(byte[] bytes) {
		byte[] result = new byte[bytes.length];
		for (int i = 0; i < bytes.length; i++) {
			result[i] = bytes[bytes.length - i - 1];
		}
		return result;
	}

	public static byte[] reverseInPlace(byte[] bytes) {
		int len = bytes.length / 2;
		for (int i = 0; i < len; i++) {
			byte t = bytes[i];
			bytes[i] = bytes[bytes.length - i - 1];
			bytes[bytes.length - i - 1] = t;
		}
		return bytes;
	}

	public static byte[] bigIntegerToBytes(BigInteger b, int numBytes) {
		if (b == null) {
			return null;
		}
		byte[] bytes = new byte[numBytes];
		byte[] biBytes = b.toByteArray();
		int start = (biBytes.length == numBytes + 1) ? 1 : 0;
		int length = Math.min(biBytes.length, numBytes);
		System.arraycopy(biBytes, start, bytes, numBytes - length, length);
		return bytes;
	}

	public static String bip38GetIntermediateCode(String password)
			throws InterruptedException {
		try {
			byte[] ownerSalt = new byte[8];
			SECURE_RANDOM.nextBytes(ownerSalt);
			byte[] passFactor = SCrypt.generate(password.getBytes("UTF-8"),
					ownerSalt, 16384, 8, 8, 32);
			ECPoint uncompressed = EC_PARAMS.getG().multiply(
					new BigInteger(1, passFactor));
			byte[] passPoint = new ECPoint.Fp(EC_PARAMS.getCurve(),
					uncompressed.getX(), uncompressed.getY(), true)
					.getEncoded();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.write(fromHex("2CE9B3E1FF39E253"));
			baos.write(ownerSalt);
			baos.write(passPoint);
			baos.write(doubleSha256(baos.toByteArray()), 0, 4);
			return encodeBase58(baos.toByteArray());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static KeyPair bip38GenerateKeyPair(String intermediateCode,
			boolean compressedPublicKey) throws InterruptedException, Exception {
		byte[] intermediateBytes = decodeBase58(intermediateCode);
		if (!verifyChecksum(intermediateBytes)
				|| intermediateBytes.length != 53) {
			throw new RuntimeException("Bad intermediate code");
		}
		byte[] magic = fromHex("2CE9B3E1FF39E2");
		for (int i = 0; i < magic.length; i++) {
			if (magic[i] != intermediateBytes[i]) {
				throw new Exception("It isn't an intermediate code");
			}
		}
		try {
			byte[] ownerEntropy = new byte[8];
			System.arraycopy(intermediateBytes, 8, ownerEntropy, 0, 8);
			byte[] passPoint = new byte[33];
			System.arraycopy(intermediateBytes, 16, passPoint, 0, 33);
			byte flag = (byte) (compressedPublicKey ? 0x20 : 0x00);// compressed
																	// public
																	// key
			byte[] seedB = new byte[24];
			SECURE_RANDOM.nextBytes(seedB);
			byte[] factorB = doubleSha256(seedB);
			BigInteger factorBInteger = new BigInteger(1, factorB);
			ECPoint uncompressedPublicKeyPoint = EC_PARAMS.getCurve()
					.decodePoint(passPoint).multiply(factorBInteger);
			String address;
			byte[] publicKey;
			if (compressedPublicKey) {
				publicKey = new ECPoint.Fp(EC_PARAMS.getCurve(),
						uncompressedPublicKeyPoint.getX(),
						uncompressedPublicKeyPoint.getY(), true).getEncoded();
				address = publicKeyToAddress(publicKey);
			} else {
				publicKey = uncompressedPublicKeyPoint.getEncoded();
				address = publicKeyToAddress(publicKey);
			}
			byte[] addressHashAndOwnerSalt = new byte[12];

			byte[] addressHash = new byte[4];
			System.arraycopy(doubleSha256(address.getBytes("UTF-8")), 0,
					addressHash, 0, 4);
			System.arraycopy(addressHash, 0, addressHashAndOwnerSalt, 0, 4);
			System.arraycopy(ownerEntropy, 0, addressHashAndOwnerSalt, 4, 8);
			byte[] derived = SCrypt.generate(passPoint,
					addressHashAndOwnerSalt, 1024, 1, 1, 64);
			byte[] key = new byte[32];
			System.arraycopy(derived, 32, key, 0, 32);
			for (int i = 0; i < 16; i++) {
				seedB[i] ^= derived[i];
			}
			AESEngine cipher = new AESEngine();
			cipher.init(true, new KeyParameter(key));
			byte[] encryptedHalf1 = new byte[16];
			byte[] encryptedHalf2 = new byte[16];
			cipher.processBlock(seedB, 0, encryptedHalf1, 0);
			byte[] secondBlock = new byte[16];
			System.arraycopy(encryptedHalf1, 8, secondBlock, 0, 8);
			System.arraycopy(seedB, 16, secondBlock, 8, 8);
			for (int i = 0; i < 16; i++) {
				secondBlock[i] ^= derived[i + 16];
			}
			cipher.processBlock(secondBlock, 0, encryptedHalf2, 0);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.write(0x01);
			baos.write(0x43);
			baos.write(flag);
			baos.write(addressHashAndOwnerSalt);
			baos.write(encryptedHalf1, 0, 8);
			baos.write(encryptedHalf2);
			baos.write(doubleSha256(baos.toByteArray()), 0, 4);
			String encryptedPrivateKey = encodeBase58(baos.toByteArray());

			byte[] pointB = generatePublicKey(factorBInteger, true);
			byte pointBPrefix = (byte) (pointB[0] ^ (derived[63] & 0x01));
			byte[] encryptedPointB = new byte[33];
			encryptedPointB[0] = pointBPrefix;
			for (int i = 0; i < 32; i++) {
				pointB[i + 1] ^= derived[i];
			}
			cipher.processBlock(pointB, 1, encryptedPointB, 1);
			cipher.processBlock(pointB, 17, encryptedPointB, 17);
			baos.reset();
			baos.write(0x64);
			baos.write(0x3B);
			baos.write(0xF6);
			baos.write(0xA8);
			baos.write(0x9A);
			baos.write(flag);
			baos.write(addressHashAndOwnerSalt);
			baos.write(encryptedPointB);
			baos.write(doubleSha256(baos.toByteArray()), 0, 4);
			String confirmationCode = encodeBase58(baos.toByteArray());

			Bip38PrivateKeyInfo privateKeyInfo = new Bip38PrivateKeyInfo(
					encryptedPrivateKey, confirmationCode, compressedPublicKey);
			return new KeyPair(address, publicKey, privateKeyInfo);

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static String bip38DecryptConfirmation(String confirmationCode,
			String password) throws Exception {
		byte[] confirmationBytes = decodeBase58(confirmationCode);
		if (!verifyChecksum(confirmationBytes)
				|| confirmationBytes.length != 55) {
			throw new RuntimeException("Bad confirmation code");
		}
		byte[] magic = fromHex("643BF6A89A");
		for (int i = 0; i < magic.length; i++) {
			if (magic[i] != confirmationBytes[i]) {
				throw new Exception("It isn't a confirmation code");
			}
		}
		try {
			byte flag = confirmationBytes[5];
			boolean compressed = (flag & 0x20) == 0x20;
			boolean lotSequencePresent = (flag & 0x04) == 0x04;
			byte[] addressHash = new byte[4];
			System.arraycopy(confirmationBytes, 6, addressHash, 0, 4);
			byte[] ownerEntropy = new byte[8];
			System.arraycopy(confirmationBytes, 10, ownerEntropy, 0, 8);
			byte[] salt = new byte[lotSequencePresent ? 4 : 8];
			System.arraycopy(ownerEntropy, 0, salt, 0, salt.length);
			byte[] encryptedPointB = new byte[33];
			System.arraycopy(confirmationBytes, 18, encryptedPointB, 0, 33);
			byte[] passFactor = SCrypt.generate(password.getBytes("UTF-8"),
					salt, 16384, 8, 8, 32);
			ECPoint uncompressed = EC_PARAMS.getG().multiply(
					new BigInteger(1, passFactor));
			byte[] passPoint = new ECPoint.Fp(EC_PARAMS.getCurve(),
					uncompressed.getX(), uncompressed.getY(), true)
					.getEncoded();

			byte[] addressHashAndOwnerSalt = new byte[12];
			System.arraycopy(addressHash, 0, addressHashAndOwnerSalt, 0, 4);
			System.arraycopy(ownerEntropy, 0, addressHashAndOwnerSalt, 4, 8);
			byte[] derived = SCrypt.generate(passPoint,
					addressHashAndOwnerSalt, 1024, 1, 1, 64);
			byte[] key = new byte[32];
			System.arraycopy(derived, 32, key, 0, 32);
			AESEngine cipher = new AESEngine();
			cipher.init(false, new KeyParameter(key));

			byte[] pointB = new byte[33];
			pointB[0] = (byte) (encryptedPointB[0] ^ (derived[63] & 0x01));
			cipher.processBlock(encryptedPointB, 1, pointB, 1);
			cipher.processBlock(encryptedPointB, 17, pointB, 17);

			for (int i = 0; i < 32; i++) {
				pointB[i + 1] ^= derived[i];
			}
			ECPoint uncompressedPublicKey;
			try {
				uncompressedPublicKey = EC_PARAMS.getCurve()
						.decodePoint(pointB)
						.multiply(new BigInteger(1, passFactor));
			} catch (RuntimeException e) {
				// point b doesn't belong the curve - bad password
				return null;
			}
			String address;
			if (compressed) {
				byte[] publicKey = new ECPoint.Fp(EC_PARAMS.getCurve(),
						uncompressedPublicKey.getX(),
						uncompressedPublicKey.getY(), true).getEncoded();
				address = CoinUtils.publicKeyToAddress(publicKey);
			} else {
				address = CoinUtils.publicKeyToAddress(uncompressedPublicKey
						.getEncoded());
			}
			byte[] decodedAddressHash = doubleSha256(address.getBytes("UTF-8"));
			for (int i = 0; i < 4; i++) {
				if (addressHash[i] != decodedAddressHash[i]) {
					return null;
				}
			}
			return address;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String bip38Encrypt(KeyPair keyPair, String password) {
		try {
			byte[] addressHash = new byte[4];
			System.arraycopy(doubleSha256(keyPair.address.getBytes("UTF-8")),
					0, addressHash, 0, 4);
			byte[] passwordDerived = SCrypt.generate(
					password.getBytes("UTF-8"), addressHash, 16384, 8, 8, 64);
			byte[] xor = new byte[32];
			System.arraycopy(passwordDerived, 0, xor, 0, 32);
			byte[] key = new byte[32];
			System.arraycopy(passwordDerived, 32, key, 0, 32);
			byte[] privateKeyBytes = getPrivateKeyBytes(keyPair.privateKey.privateKeyDecoded);
			for (int i = 0; i < 32; i++) {
				xor[i] ^= privateKeyBytes[i];
			}
			AESEngine cipher = new AESEngine();
			cipher.init(true, new KeyParameter(key));
			byte[] encryptedHalf1 = new byte[16];
			byte[] encryptedHalf2 = new byte[16];
			cipher.processBlock(xor, 0, encryptedHalf1, 0);
			cipher.processBlock(xor, 16, encryptedHalf2, 0);
			byte[] result = new byte[43];
			result[0] = 1;
			result[1] = 0x42;
			result[2] = (byte) (keyPair.privateKey.isPublicKeyCompressed ? 0xe0
					: 0xc0);
			System.arraycopy(addressHash, 0, result, 3, 4);
			System.arraycopy(encryptedHalf1, 0, result, 7, 16);
			System.arraycopy(encryptedHalf2, 0, result, 23, 16);
			MessageDigest digestSha = MessageDigest.getInstance("SHA-256");
			digestSha.update(result, 0, result.length - 4);
			System.arraycopy(digestSha.digest(digestSha.digest()), 0, result,
					39, 4);
			return encodeBase58(result);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static byte[] getPrivateKeyBytes(BigInteger privateKey) {
		byte[] privateKeyPlainNumber = privateKey.toByteArray();
		int plainNumbersOffs = privateKeyPlainNumber[0] == 0 ? 1 : 0;
		byte[] privateKeyBytes = new byte[32];
		System.arraycopy(privateKeyPlainNumber, plainNumbersOffs,
				privateKeyBytes, privateKeyBytes.length
						- (privateKeyPlainNumber.length - plainNumbersOffs),
				privateKeyPlainNumber.length - plainNumbersOffs);
		return privateKeyBytes;
	}

	public static KeyPair bip38Decrypt(String encryptedPrivateKey,
			String password) throws InterruptedException, Exception {
		byte[] encryptedPrivateKeyBytes = decodeBase58(encryptedPrivateKey);
		if (encryptedPrivateKeyBytes != null
				&& encryptedPrivateKey.startsWith("6P")
				&& verifyChecksum(encryptedPrivateKeyBytes)
				&& encryptedPrivateKeyBytes[0] == 1) {
			try {
				byte[] addressHash = new byte[4];
				System.arraycopy(encryptedPrivateKeyBytes, 3, addressHash, 0, 4);
				boolean compressed = (encryptedPrivateKeyBytes[2] & 0x20) == 0x20;
				AESEngine cipher = new AESEngine();
				if (encryptedPrivateKeyBytes[1] == 0x42) {
					byte[] encryptedSecret = new byte[32];
					System.arraycopy(encryptedPrivateKeyBytes, 7,
							encryptedSecret, 0, 32);
					byte[] passwordDerived = SCrypt.generate(
							password.getBytes("UTF-8"), addressHash, 16384, 8,
							8, 64);
					byte[] key = new byte[32];
					System.arraycopy(passwordDerived, 32, key, 0, 32);
					cipher.init(false, new KeyParameter(key));
					byte[] secret = new byte[32];
					cipher.processBlock(encryptedSecret, 0, secret, 0);
					cipher.processBlock(encryptedSecret, 16, secret, 16);
					for (int i = 0; i < 32; i++) {
						secret[i] ^= passwordDerived[i];
					}
					KeyPair keyPair = new KeyPair(new Bip38PrivateKeyInfo(
							encryptedPrivateKey, new BigInteger(1, secret),
							password, compressed));
					byte[] addressHashCalculated = new byte[4];
					System.arraycopy(
							doubleSha256(keyPair.address.getBytes("UTF-8")), 0,
							addressHashCalculated, 0, 4);
					if (!Arrays.areEqual(
							addressHashCalculated, addressHash)) {
						throw new RuntimeException("Bad password");
					}
					return keyPair;
				} else if (encryptedPrivateKeyBytes[1] == 0x43) {
					byte[] ownerSalt = new byte[8];
					System.arraycopy(encryptedPrivateKeyBytes, 7, ownerSalt, 0,
							8);
					byte[] passFactor = SCrypt.generate(
							password.getBytes("UTF-8"), ownerSalt, 16384, 8, 8,
							32);
					ECPoint uncompressed = EC_PARAMS.getG().multiply(
							new BigInteger(1, passFactor));
					byte[] passPoint = new ECPoint.Fp(EC_PARAMS.getCurve(),
							uncompressed.getX(), uncompressed.getY(), true)
							.getEncoded();
					byte[] addressHashAndOwnerSalt = new byte[12];
					System.arraycopy(encryptedPrivateKeyBytes, 3,
							addressHashAndOwnerSalt, 0, 12);
					byte[] derived = SCrypt.generate(passPoint,
							addressHashAndOwnerSalt, 1024, 1, 1, 64);
					byte[] key = new byte[32];
					System.arraycopy(derived, 32, key, 0, 32);
					cipher.init(false, new KeyParameter(key));
					byte[] decryptedHalf2 = new byte[16];
					cipher.processBlock(encryptedPrivateKeyBytes, 23,
							decryptedHalf2, 0);
					for (int i = 0; i < 16; i++) {
						decryptedHalf2[i] ^= derived[i + 16];
					}
					byte[] encryptedHalf1 = new byte[16];
					System.arraycopy(encryptedPrivateKeyBytes, 15,
							encryptedHalf1, 0, 8);
					System.arraycopy(decryptedHalf2, 0, encryptedHalf1, 8, 8);
					byte[] decryptedHalf1 = new byte[16];
					cipher.processBlock(encryptedHalf1, 0, decryptedHalf1, 0);
					for (int i = 0; i < 16; i++) {
						decryptedHalf1[i] ^= derived[i];
					}
					byte[] seedB = new byte[24];
					System.arraycopy(decryptedHalf1, 0, seedB, 0, 16);
					System.arraycopy(decryptedHalf2, 8, seedB, 16, 8);
					byte[] factorB = doubleSha256(seedB);
					BigInteger privateKey = new BigInteger(1, passFactor)
							.multiply(new BigInteger(1, factorB)).remainder(
									EC_PARAMS.getN());
					KeyPair keyPair = new KeyPair(new Bip38PrivateKeyInfo(
							encryptedPrivateKey, privateKey, password,
							compressed));
					byte[] resultedAddressHash = doubleSha256(keyPair.address
							.getBytes("UTF-8"));
					for (int i = 0; i < 4; i++) {
						if (addressHashAndOwnerSalt[i] != resultedAddressHash[i]) {
							throw new Exception("Bad password");
						}
					}
					return keyPair;
				} else {
					throw new Exception("Bad encrypted private key");
				}
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		} else {
			throw new Exception("It is not an encrypted private key");
		}
	}

}
