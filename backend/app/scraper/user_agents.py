import random
from dataclasses import dataclass

CHROME_VERSIONS = [
    "128.0.6613.119",
    "128.0.6613.113",
    "127.0.6533.119",
    "127.0.6533.99",
]

WINDOWS_VERSIONS = [
    "Windows NT 10.0; Win64; x64",
    "Windows NT 10.0; WOW64",
    "Windows NT 10.0",
]

MAC_VERSIONS = [
    "Macintosh; Intel Mac OS X 10_15_7",
    "Macintosh; Intel Mac OS X 10_15_6",
    "Macintosh; Intel Mac OS X 14_5",
]


@dataclass
class UA:
    chrome_version: str
    os: str


def random_user_agent() -> str:
    chrome = random.choice(CHROME_VERSIONS)
    os = random.choice(WINDOWS_VERSIONS + MAC_VERSIONS)
    return (
        f"Mozilla/5.0 ({os}) AppleWebKit/537.36 (KHTML, like Gecko) "
        f"Chrome/{chrome} Safari/537.36"
    )


def random_viewport() -> dict[str, int]:
    resolutions = [
        {"width": 1920, "height": 1080},
        {"width": 2560, "height": 1440},
        {"width": 1680, "height": 1050},
        {"width": 1440, "height": 900},
    ]
    return random.choice(resolutions)
