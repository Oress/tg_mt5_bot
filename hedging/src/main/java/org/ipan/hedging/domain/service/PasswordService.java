package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.domain.service.commands.users.CreateUserCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Slf4j
public class PasswordService {
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final byte[] key;

    public PasswordService(@Value("${app.encryption.keyBase64}") String key) {
        this.key = Base64.getDecoder().decode(key);
    }

    public ManagedWallet generateManagedWallet() throws IOException {
        ManagedWallet managedWallet = new ManagedWallet();

        ECKey privateKeyForNewWallet = WalletApi.generatePrivateKeyForNewWallet();
        byte[] address = privateKeyForNewWallet.getAddress();
        managedWallet.setBase58Address(WalletApi.encode58Check(address));

        byte[] privateKey = privateKeyForNewWallet.getPrivKeyBytes();
        byte[] encryptedPrivateKey = encrypt(privateKey, key);
        managedWallet.setPrivateKeyEncrypted(encryptedPrivateKey);

        return managedWallet;
    }

    public String sign(ManagedWallet managedWallet, String hexString) {
        byte[] dataToSign = ByteArray.fromHexString(hexString);
        byte[] decryptedPrivateKey = decrypt(managedWallet.getPrivateKeyEncrypted(), key);
        ECKey ecKey = ECKey.fromPrivate(decryptedPrivateKey);
        return ecKey.sign(dataToSign).toHex();
    }

    @SneakyThrows
    private static byte[] encrypt(byte[] plaintext, byte[] key) {
        Cipher cipher = Cipher.getInstance(ALGO);
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
        byte[] encrypted = cipher.doFinal(plaintext);

        // prepend IV for storage
        byte[] result = new byte[IV_LENGTH + encrypted.length];
        System.arraycopy(iv, 0, result, 0, IV_LENGTH);
        System.arraycopy(encrypted, 0, result, IV_LENGTH, encrypted.length);
        return result;
    }

    @SneakyThrows
    private static byte[] decrypt(byte[] ciphertext, byte[] key) {
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        return cipher.doFinal(ciphertext, IV_LENGTH, ciphertext.length - IV_LENGTH);
    }
}
