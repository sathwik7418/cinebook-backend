package com.cinebook.notification;

import com.cinebook.notification.EmailOutboxService.EmailOutboxItem;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.logging.Logger;

/** Background retry worker; provider outage never affects the persisted booking. */
@Component
public class EmailDispatchWorker {
    private static final Logger LOG = Logger.getLogger(EmailDispatchWorker.class.getName());
    private final EmailOutboxService outbox; private final EmailDeliveryService delivery;
    public EmailDispatchWorker(EmailOutboxService outbox, EmailDeliveryService delivery) { this.outbox=outbox;this.delivery=delivery; }

    @Scheduled(fixedDelayString = "${cinebook.email.worker-delay-ms:15000}")
    public void process() {
        for (EmailOutboxItem item : outbox.claimReady()) {
            boolean accepted = delivery.send(item.to, item.subject, item.text, item.html);
            outbox.finish(item.id, accepted);
            if (!accepted) LOG.warning("Transactional email attempt failed; event=" + item.eventKey + ", attempt=" + item.attempts);
        }
    }
}
