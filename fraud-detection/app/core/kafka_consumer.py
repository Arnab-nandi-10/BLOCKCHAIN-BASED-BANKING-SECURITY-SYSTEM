import json
import threading
from typing import TYPE_CHECKING, Any, Dict

import structlog

if TYPE_CHECKING:
    from app.services.scoring_service import ScoringService

logger = structlog.get_logger(__name__)


class FraudKafkaConsumer:
    """Kafka consumer that reads from the 'tx.submitted' topic and scores each
    transaction for fraud via ScoringService.

    Designed to run in a daemon thread so it does not block the main process.
    """

    def __init__(self, scoring_service: "ScoringService") -> None:
        from kafka import KafkaConsumer  # type: ignore[import-untyped]

        from app.core.config import settings

        self.scoring_service = scoring_service
        self._running: bool = False
        self._lock = threading.Lock()

        self.consumer: KafkaConsumer = KafkaConsumer(
            "tx.submitted",
            bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
            group_id=settings.KAFKA_GROUP_ID,
            value_deserializer=lambda raw: json.loads(raw.decode("utf-8")),
            auto_offset_reset="latest",
            enable_auto_commit=True,
            consumer_timeout_ms=1000,  # raises StopIteration after 1 s of silence
        )

        logger.info(
            "FraudKafkaConsumer initialised",
            bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
            group_id=settings.KAFKA_GROUP_ID,
            topics=["tx.submitted"],
        )

    def start(self) -> None:
        """Poll messages in a blocking loop.  Call from a daemon thread."""
        with self._lock:
            self._running = True

        logger.info("Kafka consumer polling loop started")

        try:
            while self._running:
                # poll() returns a dict[TopicPartition, list[ConsumerRecord]]
                raw_messages: Dict[Any, Any] = self.consumer.poll(timeout_ms=1000)
                for _topic_partition, records in raw_messages.items():
                    for record in records:
                        if not self._running:
                            break
                        try:
                            self.scoring_service.score_transaction_async(record.value)
                        except Exception as exc:
                            logger.error(
                                "Error processing Kafka message",
                                error=str(exc),
                                offset=record.offset,
                            )
        except Exception as exc:
            logger.error("Kafka consumer loop terminated with error", error=str(exc))
        finally:
            try:
                self.consumer.close()
            except Exception as exc:
                logger.warning("Error closing Kafka consumer", error=str(exc))
            logger.info("Kafka consumer stopped")

    def stop(self) -> None:
        """Signal the polling loop to exit on its next iteration."""
        with self._lock:
            self._running = False
        logger.info("Kafka consumer stop signal sent")
