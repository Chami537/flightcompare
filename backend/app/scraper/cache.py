import time
import logging
from functools import wraps
from typing import Any, Callable, Coroutine

from app.config import settings

logger = logging.getLogger(__name__)

# In-memory cache: key -> (timestamp, value)
_cache: dict[str, tuple[float, Any]] = {}


def cache_key(*args, **kwargs) -> str:
    return str((args, sorted(kwargs.items())))


def cached(ttl: int | None = None):
    """Decorator for async function result caching."""
    ttl_seconds = ttl or settings.cache_ttl_seconds

    def decorator(func: Callable[..., Coroutine]):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            key = f"{func.__name__}:{cache_key(*args, **kwargs)}"
            now = time.time()

            if key in _cache:
                ts, value = _cache[key]
                if now - ts < ttl_seconds:
                    logger.debug(f"Cache hit for {func.__name__}")
                    return value

            result = await func(*args, **kwargs)
            _cache[key] = (now, result)

            # Purge expired entries
            expired = [k for k, (ts, _) in _cache.items() if now - ts > ttl_seconds * 2]
            for k in expired:
                del _cache[k]

            return result

        return wrapper

    return decorator


def clear_cache():
    global _cache
    _cache.clear()
