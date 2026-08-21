package com.motodigo.core.notification.job;

import com.motodigo.core.notification.service.NotificationDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationDispatchScheduler {

	private static final Logger log = LoggerFactory.getLogger(NotificationDispatchScheduler.class);

	private final NotificationDispatchService dispatchService;

	public NotificationDispatchScheduler(NotificationDispatchService dispatchService) {
		this.dispatchService = dispatchService;
	}

	@Scheduled(cron = "${motodigo.push.dispatch-cron:0 */1 * * * *}")
	public void processQueued() {
		int processed = this.dispatchService.processQueuedBatch(50);
		if (processed > 0) {
			log.info("Dispatched {} queued notification(s)", processed);
		}
	}
}
