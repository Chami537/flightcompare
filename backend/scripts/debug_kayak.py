"""Debug Kayak page structure."""
import asyncio, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))

from app.scraper.engine import start_browser, stop_browser
from app.scraper.user_agents import random_user_agent

STEALTH_JS = """
Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
"""

async def main():
    browser = await start_browser()
    context = await browser.new_context(
        user_agent=random_user_agent(),
        viewport={"width": 1920, "height": 1080},
        locale="en-US",
    )
    page = await context.new_page()
    await page.add_init_script(STEALTH_JS)

    # Kayak URL format: https://www.kayak.com/flights/{origin}-{dest}/{depart}/{return}
    url = "https://www.kayak.com/flights/JFK-LAX/2026-06-20/2026-06-27?sort=price_a"
    print(f"Navigating to: {url}")

    try:
        await page.goto(url, wait_until="domcontentloaded", timeout=30000)
        await asyncio.sleep(10)
    except Exception as e:
        print(f"Nav error: {e}")

    title = await page.title()
    print(f"Title: {title}")

    try:
        text = await page.locator("body").inner_text(timeout=3000)
        print(f"\n--- Body (first 3000 chars) ---\n{text[:3000]}")
    except Exception as e:
        print(f"Text error: {e}")

    # Check for price elements
    for sel in ['[class*="price"]', '[class*="Price"]', '.price-text',
                'span:has-text("$")', 'div:has-text("$")']:
        try:
            c = await page.locator(sel).count()
            if c: print(f"'{sel}': {c}")
        except: pass

    await page.screenshot(path=str(Path(__file__).parent / "kayak_debug.png"))
    print("Screenshot saved")

    await context.close()
    await stop_browser()

if __name__ == "__main__":
    asyncio.run(main())
