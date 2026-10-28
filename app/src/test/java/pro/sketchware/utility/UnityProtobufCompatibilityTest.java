package pro.sketchware.utility;

import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the SDK message that failed Android verification during initialization. */
public class UnityProtobufCompatibilityTest {
    @Test public void unityTimestampsRoundTripAlongsideFullProtobuf() throws Exception {
        // Bundletool still needs the full well-known types and their descriptors.
        Class<?> full = Class.forName("com.google.protobuf.Timestamp");
        assertNotNull(full.getMethod("getDescriptor").invoke(null));

        Class<?> message = Class.forName("gatewayprotocol.v1.TimestampsOuterClass$Timestamps");
        Class<?> timestamp = message.getMethod("getTimestamp").getReturnType();
        Object timeBuilder = timestamp.getMethod("newBuilder").invoke(null);
        timeBuilder.getClass().getMethod("setSeconds", long.class).invoke(timeBuilder, 123L);
        Object time = timeBuilder.getClass().getMethod("build").invoke(timeBuilder);
        Object builder = message.getMethod("newBuilder").invoke(null);
        builder.getClass().getMethod("setTimestamp", timestamp).invoke(builder, time);
        Object value = builder.getClass().getMethod("build").invoke(builder);
        byte[] bytes = (byte[]) message.getMethod("toByteArray").invoke(value);
        Object parsed = message.getMethod("parseFrom", byte[].class).invoke(null, bytes);
        assertEquals(123L, timestamp.getMethod("getSeconds").invoke(message.getMethod("getTimestamp").invoke(parsed)));
        assertNotEquals(full, timestamp);
    }

    public static void main(String[] args) throws Exception {
        new UnityProtobufCompatibilityTest().unityTimestampsRoundTripAlongsideFullProtobuf();
        System.out.println("Unity lite timestamp round-trip and full protobuf descriptors: PASS");
    }
}
