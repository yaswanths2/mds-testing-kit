package io.mosip.mds.entitiy;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource.PSpecified;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.mds.dto.CaptureResponse;
import io.mosip.mds.dto.CaptureResponse.CaptureBiometric;
import io.mosip.mds.dto.DigitalId;
import io.mosip.mds.util.CryptoUtility;
import io.mosip.mds.util.SecurityUtil;;

public class CaptureHelper {
	
	private static ObjectMapper mapper;
	
	static {
		mapper = new ObjectMapper();
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	}

	private static String RCAPTURE = "rCapture";
	private static String CAPTURE = "Capture";
	private static String RCAPTURE_DECODE_ERROR = "Error while decoding the " + CAPTURE + " request";
	// private static String CAPTURE_DECODE_ERROR = "Error while decoding the " +
	// CAPTURE + " request";

	private static String PAYLOAD_EMPTY = "PayLoad Empty";

	public static CaptureResponse decode(String responseInfo, boolean isRCapture) {

		CaptureResponse response = null;

		// Deserialize Capture Response
		try {
			response = (CaptureResponse) (mapper.readValue(responseInfo.getBytes(), CaptureResponse.class));

			for (CaptureResponse.CaptureBiometric biometric : response.biometrics) {

				if (biometric.getData() == null) {
					response.setAnalysisError(RCAPTURE_DECODE_ERROR + " : data empty");
					break;
				}
				
				try {

					// Decode data
					biometric.setDataDecoded((CaptureResponse.CaptureBiometricData) (mapper.readValue(
							SecurityUtil.getPayload(biometric.getData()), CaptureResponse.CaptureBiometricData.class)));

					if (!isRCapture) {

						if (biometric.getSessionKey() == null) {
							response.analysisError = RCAPTURE_DECODE_ERROR + " : " + "Session Key is empty";
							break;
						}

						String decryptedBioValue = getDecryptedBioValue(biometric);
						biometric.getDataDecoded().setBioValue(decryptedBioValue);
					}
					// TODO Verify Digital Id with mock mds
					// Decode.DigitalId

					if (biometric.getDataDecoded().getDigitalId() != null) {
						biometric.getDataDecoded()
								.setDigitalIdDecoded((DigitalId) (mapper.readValue(SecurityUtil.getPayload(biometric.getDataDecoded().getDigitalId()),
										DigitalId.class)));
					} else {
						response.analysisError = RCAPTURE_DECODE_ERROR + " : " + "digital id is empty";
						break;
					}

				} catch (IllegalArgumentException illegalArgumentException) {
					response.setAnalysisError(RCAPTURE_DECODE_ERROR + " : Error while decoding payload"
							+ illegalArgumentException.getMessage());
				}

			}
		} catch (Exception exception) {
			exception.printStackTrace();
			response = new CaptureResponse();
			response.setAnalysisError(RCAPTURE_DECODE_ERROR + exception.getMessage());
		}

		return response;

	}

	private static String getDecryptedBioValue(CaptureBiometric biometric) {
		PrivateKey privateKey = getPrivateKey();
		
		String plainBioValue = CryptoUtility.decrypt(privateKey, biometric.sessionKey, biometric.getDataDecoded().bioValue, 
				biometric.getDataDecoded().timestamp);		
		return Base64.getUrlEncoder().encodeToString(plainBioValue.getBytes());
	}
	
	public static PrivateKey getPrivateKey() {		
		try {
			FileInputStream pkeyfis = new FileInputStream("data/keys/PrivateKey.pem");

			String pKey = getFileContent(pkeyfis, "UTF-8");
			pKey = trimBeginEnd(pKey);
			KeyFactory kf = KeyFactory.getInstance("RSA");
			return kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pKey)));
		} catch (Exception ex) {
			ex.printStackTrace();
			//throw new Exception("Failed to get private key");
		}
		return null;
	}
	
	public static String getFileContent(FileInputStream fis, String encoding) throws IOException {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(fis, encoding))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line);
				sb.append('\n');
			}
			return sb.toString();
		}
	}

	private static String trimBeginEnd(String pKey) {
		pKey = pKey.replaceAll("-*BEGIN([^-]*)-*(\r?\n)?", "");
		pKey = pKey.replaceAll("-*END([^-]*)-*(\r?\n)?", "");
		pKey = pKey.replaceAll("\\s", "");
		return pKey;
	}	

	public static File extractImage(String bioValue, String bioType) {
		// do base64 url decoding
		byte[] decodedData = Base64.getUrlDecoder().decode(bioValue);
		// strip iso header
		byte[] imageData = extractJPGfromISO(decodedData, bioType);
		// save image to file
		String fileName = "data/renders/" + UUID.randomUUID() + ".jp2";

		File file = new File(fileName);
		try {
			if (file.createNewFile()) {
				OutputStream writer = new FileOutputStream(file);
				writer.write(imageData);
				writer.close();
			}
		} catch (Exception ex) {
			file = null;
		}
		return file;
	}

	private static byte[] extractJPGfromISO(byte[] isoValue, String bioType) {
		// TODO set the correct iso handling technique here
		int isoHeaderSize = 0;
		byte hasCertBlock = 0;
		int recordLength = 0;
		int sizeIndex = 0;
		int imageSize = 0;
		int qbSize = 0;
		int cbSize = 0;
		if (bioType.equalsIgnoreCase("Finger")) {
			hasCertBlock = isoValue[14];
			qbSize = isoValue[34] * 5;
			cbSize = (hasCertBlock == 1) ? hasCertBlock + (isoValue[35 + qbSize] * 3) : 0;
			recordLength = ByteBuffer.wrap(Arrays.copyOfRange(isoValue, 8, 12)).getInt();
			sizeIndex = 35 + qbSize + cbSize + 18;
			imageSize = ByteBuffer.wrap(Arrays.copyOfRange(isoValue, sizeIndex, sizeIndex + 4)).getInt();
			isoHeaderSize = sizeIndex + 4;
		} else if (bioType.equalsIgnoreCase("Face")) {
			hasCertBlock = isoValue[14];
			qbSize = isoValue[35] * 5;
			// cbSize = (hasCertBlock == 1) ? hasCertBlock + (isoValue[35 + qbSize] * 3) :
			// 0;
			recordLength = ByteBuffer.wrap(Arrays.copyOfRange(isoValue, 8, 12)).getInt();
			int landmarkPoints = ByteBuffer.wrap(Arrays.copyOfRange(isoValue, 36 + qbSize, 36 + qbSize + 4)).getShort();
			sizeIndex = 36 + qbSize + (landmarkPoints * 8) + cbSize + 28;
			imageSize = ByteBuffer.wrap(Arrays.copyOfRange(isoValue, sizeIndex, sizeIndex + 4)).getInt();
			isoHeaderSize = sizeIndex + 4;
		} else if (bioType.equalsIgnoreCase("Iris")) {
			hasCertBlock = isoValue[14];
			qbSize = isoValue[34] * 5;
			// cbSize = (hasCertBlock == 1) ? hasCertBlock + (isoValue[35 + qbSize] * 3) :
			// 0;
			recordLength = ByteBuffer.wrap(Arrays.copyOfRange(isoValue, 8, 12)).getInt();
			sizeIndex = 35 + qbSize + cbSize + 29;
			imageSize = ByteBuffer.wrap(Arrays.copyOfRange(isoValue, sizeIndex, sizeIndex + 4)).getInt();
			isoHeaderSize = sizeIndex + 4;
		}
		return Arrays.copyOfRange(isoValue, isoHeaderSize, isoHeaderSize + imageSize);
	}
}