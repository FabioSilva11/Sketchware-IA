package pro.sketchware.store.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Hashcash: find a nonce so that SHA-256(input + ":" + nonce) starts with {@code bits} zero bits.
 * Cheap to check, costly to produce, so flooding the network with identities or events costs
 * the sender real work.
 */
public final class ProofOfWork {

    private ProofOfWork() {
    }

    public static long solve(String input, int bits) {
        MessageDigest digest = Codec.digest("SHA-256");
        byte[] prefix = (input + ":").getBytes(StandardCharsets.UTF_8);
        for (long nonce = 0; ; nonce++) {
            digest.reset();
            digest.update(prefix);
            digest.update(Long.toString(nonce).getBytes(StandardCharsets.UTF_8));
            if (leadingZeroBits(digest.digest()) >= bits) {
                return nonce;
            }
        }
    }

    public static boolean check(String input, long nonce, int bits) {
        if (nonce < 0) {
            return false;
        }
        byte[] hash = Codec.sha256((input + ":" + nonce).getBytes(StandardCharsets.UTF_8));
        return leadingZeroBits(hash) >= bits;
    }

    private static int leadingZeroBits(byte[] hash) {
        int bits = 0;
        for (byte b : hash) {
            if (b == 0) {
                bits += 8;
                continue;
            }
            bits += Integer.numberOfLeadingZeros(b & 0xff) - 24;
            break;
        }
        return bits;
    }
}
