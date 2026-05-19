import asyncio
import logging
import random
import time
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class TokenBucket:
    """Token bucket rate limiter for scraping."""
    rate: float  # tokens per second
    burst: int
    tokens: float = 0.0
    last_refill: float = 0.0

    def __post_init__(self):
        self.tokens = float(self.burst)
        self.last_refill = time.monotonic()

    async def acquire(self):
        while True:
            now = time.monotonic()
            elapsed = now - self.last_refill
            self.tokens = min(float(self.burst), self.tokens + elapsed * self.rate)
            self.last_refill = now

            if self.tokens >= 1:
                self.tokens -= 1
                return
            else:
                wait = (1 - self.tokens) / self.rate
                jitter = random.uniform(0, wait * 0.3)
                logger.debug(f"Rate limit: waiting {wait + jitter:.1f}s")
                await asyncio.sleep(wait + jitter)
