package org.umd.spore.cloud;

import com.yahoo.ycsb.ByteArrayByteIterator;
import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.StringByteIterator;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Iterator;
import java.util.Map;

/**
 * @author vlekakis
 * Basic spore utils class that helps with
 *  - key generation
 *  - digital signatures 
 *  - read/write security keys to disk
 */

@Data
public class SporeUtils {
    
    private String publicKeyPath;
    private String privateKeyPath;
    private KeyPair keyPair;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private Signature signObj;

    /**
     * Function to either load the keys from disk or to generate them.
     * The function generates them if while trying to find the keys get
     * and an Exception
     */
    public void loadKeys() {
        try {
            readKeys();
        } catch (Exception e) {
            keyGeneration();
        }
    }
    
    //TODO: verification of record
    //TODO: verification of field


    /**
     * 
     * Record-based signature
     *
     * @param values The record to be inserted in the data store
     * @return The record with an extra field that represents the signature of the record
     * @throws Exception
     */
    public Map<String, ByteIterator> signRecord(Map<String, ByteIterator>values) throws Exception {
        byte[] mapBytes = StringByteIterator.getStringMap(values).toString().getBytes(StandardCharsets.UTF_8);
        signObj.update(mapBytes);
        values.put("sign", new ByteArrayByteIterator(signObj.sign()));
        return values;
    }

    /**
     * *
     * @param values The record to be signed
     * @return The record with all the fields now having a signature at their end
     * @throws Exception
     */
    public Map<String, ByteIterator> signFields(Map<String, ByteIterator>values) throws Exception {
        for (String s:values.keySet()) {
            ByteIterator valueIt = values.get(s);
            byte[] fieldBytes = valueIt.toString().getBytes(StandardCharsets.UTF_8);
            signObj.update(fieldBytes);
            byte[] signature = signObj.sign();
            byte[] signedField = new byte[fieldBytes.length+signature.length];
            System.arraycopy(fieldBytes, 0, signedField, 0, fieldBytes.length);
            System.arraycopy(signature, 0, signedField, fieldBytes.length, signature.length);
            values.put(s,new ByteArrayByteIterator(signedField));
        }
        return values;
    }
    
    private void keyGeneration() {
        try {
            KeyPairGenerator pairGenerator = KeyPairGenerator.getInstance("RSA", "SUN");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            pairGenerator.initialize(1024, random);
            
            keyPair = pairGenerator.generateKeyPair();
            privateKey = keyPair.getPrivate();
            publicKey = keyPair.getPublic();
            signObj = Signature.getInstance("SHA1withRSA", "SUN");
            signObj.initSign(privateKey);
            saveKeys();

        } catch (Exception e) {

        }
    }
    
    
    private void saveKeys() throws IOException{
        if (StringUtils.isNotBlank(publicKeyPath)) {
            saveKeyBytes(publicKey.getEncoded(), publicKeyPath);
        }
        if (StringUtils.isNotBlank(privateKeyPath)) {
            saveKeyBytes(privateKey.getEncoded(), privateKeyPath);
        }
    }
    
    private void readKeys() throws Exception {
        byte[] keyBytes;
        KeyFactory keyFactory = KeyFactory.getInstance("RSA", "SUN");
        
        if (StringUtils.isNotBlank(publicKeyPath)) {
            keyBytes = readKeyBytes(publicKeyPath);
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(keyBytes);
            publicKey = keyFactory.generatePublic(pubKeySpec);
        }
        if (StringUtils.isNotBlank(privateKeyPath)) {
            keyBytes = readKeyBytes(privateKeyPath);
            PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(keyBytes);
            privateKey = keyFactory.generatePrivate(privKeySpec);
            signObj = Signature.getInstance("SHA1withRSA", "SUN");
            signObj.initSign(privateKey);
        }
    }
    
    private void saveKeyBytes(byte[] keyBytes, String path) throws IOException {
        FileOutputStream fOut = new FileOutputStream(path);
        fOut.write(keyBytes);
        fOut.close();
    }
    
    private byte[] readKeyBytes(String path) throws IOException {
        FileInputStream fIn = new FileInputStream(path);
        byte[] keyBytes = new byte[fIn.available()];
        fIn.read(keyBytes);
        fIn.close();
        return keyBytes;
    }
    
}

