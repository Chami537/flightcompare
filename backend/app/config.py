from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "sqlite+aiosqlite:///./flights.db"
    google_flights_base_url: str = "https://www.google.com/travel/flights"
    scraper_rate_limit_seconds: float = 20.0  # min interval between scrapes
    scraper_burst_limit: int = 3
    cache_ttl_seconds: int = 1800  # 30 min cache for search results
    proxy_url: str | None = None
    log_level: str = "INFO"

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()
