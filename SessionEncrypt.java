package com.iboxendriverapp;

import org.libsodium.jni.SodiumConstants;
import org.libsodium.jni.crypto.Box;
import org.libsodium.jni.crypto.Random;
import org.libsodium.jni.crypto.SecretBox;
import org.libsodium.jni.keys.KeyPair;
import org.libsodium.jni.keys.PublicKey;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class SessionEncrypt {
    private KeyPair keypair;

    SessionEncrypt() {
        this.keypair = new KeyPair(new Random().randomBytes(SodiumConstants.SECRETKEY_BYTES));
    }

    public byte[] generateSessionKey(byte[] data, byte[] public_key) {
        Box box = new Box(new PublicKey(public_key), keypair.getPrivateKey());
        byte[] sessionKey = box.decrypt(Arrays.copyOfRange(data, 0, SodiumConstants.NONCE_BYTES), Arrays.copyOfRange(data, SodiumConstants.NONCE_BYTES, data.length));

        return sessionKey;
    }

    public byte[] decryptMessage(byte[] key, byte[] data) {
        byte[] nonce = Arrays.copyOfRange(data, 0, SodiumConstants.NONCE_BYTES);
        byte[] cipher = Arrays.copyOfRange(data, SodiumConstants.NONCE_BYTES, data.length);

        SecretBox sb = new SecretBox(key);

        byte[] message = sb.decrypt(nonce, cipher);

        return message;
    }

    public byte[] encryptMessage(byte[] key, byte[] message) {
        SecretBox sb = new SecretBox(key);
        byte[] nonce = new Random().randomBytes(SodiumConstants.NONCE_BYTES);
        byte[] cipher = sb.encrypt(nonce, message);

        ByteArrayOutputStream strm = new ByteArrayOutputStream();
        try {
            strm.write(nonce);
            strm.write(cipher);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return strm.toByteArray();
    }

    public byte[] getPublicKey() {
        return keypair.getPublicKey().toBytes();
    }
}

