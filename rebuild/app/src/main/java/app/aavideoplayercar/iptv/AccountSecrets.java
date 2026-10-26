package app.aavideoplayercar.iptv;
import android.security.keystore.*;
import android.util.Base64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.util.Arrays;
/** Device-bound encryption. Ciphertext is scoped to its account ID through authenticated data. */
public final class AccountSecrets {
    private static final String ALIAS = "aa-player-xtream-v1";
    private static synchronized javax.crypto.SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            if (!create) throw new IOException("Account key unavailable");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generator.generateKey();
        }
        return (javax.crypto.SecretKey)store.getKey(ALIAS, null);
    }
    public static String encrypt(String accountId, String plaintext) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(true));
            cipher.updateAAD(accountId.getBytes(StandardCharsets.UTF_8)); byte[] payload = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = new byte[12 + payload.length]; System.arraycopy(cipher.getIV(), 0, bytes, 0, 12); System.arraycopy(payload, 0, bytes, 12, payload.length);
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception error) { throw new IOException("Could not secure account credentials"); }
    }
    public static String decrypt(String accountId, String encoded) throws IOException {
        try {
            byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP); if (bytes.length < 28 || bytes.length > 16384) throw new IOException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
            cipher.updateAAD(accountId.getBytes(StandardCharsets.UTF_8)); return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (Exception error) { throw new IOException("Account credentials unavailable; sign in again"); }
    }
}
