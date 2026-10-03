package pro.sketchware.store.core;

import org.libtorrent4j.Ed25519;
import org.libtorrent4j.Pair;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * A store user: an Ed25519 key pair. The public key, in hex, is the user's id; every record the
 * user publishes is signed with it. Creating one costs a proof of work (see {@link ProofOfWork}),
 * which makes throwaway identities for faking likes and downloads more expensive.
 */
public final class Identity {

    /** Leading zero bits the identity proof needs; about four million hashes. */
    public static final int IDENTITY_WORK_BITS = 22;

    private final byte[] seed;
    private final byte[] publicKey;
    private final byte[] secretKey;
    private final long proof;

    private Identity(byte[] seed, long proof) {
        this.seed = seed;
        Pair<byte[], byte[]> keys = Ed25519.createKeypair(seed);
        publicKey = keys.first;
        secretKey = keys.second;
        this.proof = proof;
    }

    /** Loads the identity saved in {@code file}, creating it (with its proof of work) if needed. */
    public static Identity loadOrCreate(File file) throws IOException {
        if (file.isFile()) {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length == Ed25519.SEED_SIZE + 8) {
                byte[] seed = Arrays.copyOf(bytes, Ed25519.SEED_SIZE);
                long proof = 0;
                for (int i = 0; i < 8; i++) {
                    proof = (proof << 8) | (bytes[Ed25519.SEED_SIZE + i] & 0xff);
                }
                Identity identity = new Identity(seed, proof);
                if (verifyProof(identity.id(), proof)) {
                    return identity;
                }
            }
        }
        byte[] seed = Ed25519.createSeed();
        Identity unproven = new Identity(seed, 0);
        long proof = ProofOfWork.solve(proofInput(unproven.id()), IDENTITY_WORK_BITS);
        Identity identity = new Identity(seed, proof);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        byte[] out = Arrays.copyOf(seed, Ed25519.SEED_SIZE + 8);
        for (int i = 0; i < 8; i++) {
            out[Ed25519.SEED_SIZE + i] = (byte) (proof >>> (56 - 8 * i));
        }
        File temp = new File(file.getPath() + ".tmp");
        Files.write(temp.toPath(), out);
        if (!temp.renameTo(file)) {
            Files.write(file.toPath(), out);
            temp.delete();
        }
        return identity;
    }

    /** A fixed, published key pair: used for the shared directory every node may write to. */
    public static Identity wellKnown(String label) {
        return new Identity(Codec.sha256(label.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 0);
    }

    private static String proofInput(String id) {
        return "swia-identity:" + id;
    }

    public static boolean verifyProof(String id, long proof) {
        return Codec.isHex(id, 64) && ProofOfWork.check(proofInput(id), proof, IDENTITY_WORK_BITS);
    }

    public String id() {
        return Codec.hex(publicKey);
    }

    public long proof() {
        return proof;
    }

    public byte[] publicKey() {
        return publicKey.clone();
    }

    public byte[] secretKey() {
        return secretKey.clone();
    }

    public byte[] sign(byte[] message) {
        return Ed25519.sign(message, publicKey, secretKey);
    }

    public static boolean verify(String authorId, byte[] message, byte[] signature) {
        if (!Codec.isHex(authorId, 64) || signature == null || signature.length != Ed25519.SIGNATURE_SIZE) {
            return false;
        }
        return Ed25519.verify(signature, message, Codec.unhex(authorId));
    }
}
