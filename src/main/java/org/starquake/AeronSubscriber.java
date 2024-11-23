package org.starquake;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Image;
import io.aeron.Subscription;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.DirectBuffer;

public class AeronSubscriber {
    private static final String CHANNEL = "aeron:udp?endpoint=localhost:40456";
    private static final int STREAM_ID = 10;
    private static final int FRAGMENT_LIMIT = 10;
    private static final IdleStrategy IDLE_STRATEGY = new BackoffIdleStrategy(1, 10, 1000, 1000);

    public static void main(String[] args) {
        try (Aeron aeron = Aeron.connect();
             Subscription subscription = aeron.addSubscription(CHANNEL, STREAM_ID)) {

            FragmentAssembler fragmentAssembler = new FragmentAssembler(AeronSubscriber::onMessage);
            
            while (true) {
                int fragmentsRead = subscription.poll(fragmentAssembler, FRAGMENT_LIMIT);
                if (fragmentsRead == 0) {
                    IDLE_STRATEGY.idle();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void onMessage(DirectBuffer buffer, int offset, int length, Header header) {
        String message = buffer.getStringWithoutLengthAscii(offset, length);
        System.out.println("Received message: " + message);
    }
}
