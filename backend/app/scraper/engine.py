import logging
from contextlib import asynccontextmanager

from playwright.async_api import Browser, async_playwright

logger = logging.getLogger(__name__)

_browser: Browser | None = None


async def start_browser():
    global _browser
    if _browser is not None and _browser.is_connected():
        return _browser
    pw = await async_playwright().start()
    _browser = await pw.chromium.launch(
        headless=True,
        args=[
            "--disable-blink-features=AutomationControlled",
            "--no-sandbox",
            "--disable-infobars",
        ],
    )
    logger.info("Browser launched")
    return _browser


async def stop_browser():
    global _browser
    if _browser:
        await _browser.close()
        _browser = None
        logger.info("Browser closed")


def get_browser() -> Browser | None:
    return _browser
