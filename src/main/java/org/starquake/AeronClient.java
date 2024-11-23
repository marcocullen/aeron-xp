package org.starquake;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;

public class AeronClient {
    private static final String CHANNEL = "aeron:udp?endpoint=localhost:40456";
    private static final int STREAM_ID = 10;
    private static final int MESSAGE_LENGTH = 256;

    public static void main(String[] args) {
        try (Aeron aeron = Aeron.connect();
             Publication publication = aeron.addPublication(CHANNEL, STREAM_ID)) {

            UnsafeBuffer buffer = new UnsafeBuffer(new byte[MESSAGE_LENGTH]);
            String message = "Hello, Aeron!";
            buffer.putStringWithoutLengthAscii(0, message);

            while (publication.offer(buffer, 0, MESSAGE_LENGTH) < 0L) {
                System.out.println("Offer failed");
                Thread.sleep(10);
            }

            System.out.println("Message sent: " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
