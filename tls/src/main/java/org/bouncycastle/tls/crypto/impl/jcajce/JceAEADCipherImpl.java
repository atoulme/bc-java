package org.bouncycastle.tls.crypto.impl.jcajce;

import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.cms.GCMParameters;
import org.bouncycastle.jcajce.spec.AEADParameterSpec;
import org.bouncycastle.jcajce.util.JcaJceHelper;
import org.bouncycastle.tls.crypto.impl.TlsAEADCipherImpl;

/**
 * A basic wrapper for a JCE Cipher class to provide the needed AEAD cipher functionality for TLS.
 */
public class JceAEADCipherImpl
    implements TlsAEADCipherImpl
{
    private static String getAlgParamsName(JcaJceHelper helper, String cipherName)
    {
        try
        {
            String algName = cipherName.contains("CCM") ? "CCM" : "GCM";
            helper.createAlgorithmParameters(algName);
            return algName;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private final JcaJceHelper helper;
    private final int cipherMode;
    private final Cipher cipher;
    private final String algorithm;
    private final String algorithmParamsName;

    private SecretKey key;

    public JceAEADCipherImpl(JcaJceHelper helper, String cipherName, String algorithm, boolean isEncrypting)
        throws GeneralSecurityException
    {
        this.helper = helper;
        this.cipher = helper.createCipher(cipherName);
        this.algorithm = algorithm;
        this.cipherMode = (isEncrypting) ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;
        this.algorithmParamsName = getAlgParamsName(helper, cipherName);
    }

    public void setKey(byte[] key, int keyOff, int keyLen)
    {
        this.key = new SecretKeySpec(key, keyOff, keyLen, algorithm);
    }

    public void init(byte[] nonce, int macSize, byte[] additionalData)
    {
        try
        {
            if (algorithmParamsName != null)
            {
                AlgorithmParameters algParams = helper.createAlgorithmParameters(algorithmParamsName);

                // fortunately CCM and GCM parameters have the same ASN.1 structure
                algParams.init(new GCMParameters(nonce, macSize).getEncoded());

                cipher.init(cipherMode, key, algParams);

                if (additionalData != null && additionalData.length > 0)
                {
                    cipher.updateAAD(additionalData);
                }
            }
            else
            {
                // Otherwise fall back to the BC-specific AEADParameterSpec
                cipher.init(cipherMode, key, new AEADParameterSpec(nonce, macSize * 8, additionalData));
            }
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public int getOutputSize(int inputLength)
    {
        return cipher.getOutputSize(inputLength);
    }

    public int doFinal(byte[] input, int inputOffset, int inputLength, byte[] output, int outputOffset)
    {
        try
        {
            return cipher.doFinal(input, inputOffset, inputLength, output, outputOffset);
        }
        catch (GeneralSecurityException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
