import json
import logging
from pathlib import Path

from fastapi import APIRouter, Query

from app.schemas import AirportResponse

logger = logging.getLogger(__name__)

DATA_DIR = Path(__file__).resolve().parent.parent / "data"
AIRPORTS_FILE = DATA_DIR / "airports.json"

AIRPORTS: list[dict] = []
try:
    with open(AIRPORTS_FILE) as f:
        AIRPORTS = json.load(f)
    logger.info(f"Loaded {len(AIRPORTS)} airports from {AIRPORTS_FILE}")
except FileNotFoundError:
    logger.warning("airports.json not found -- airport autocomplete will return empty")
except json.JSONDecodeError:
    logger.warning("airports.json is malformed -- airport autocomplete will return empty")

router = APIRouter(prefix="/airports", tags=["airports"])


@router.get("/", response_model=list[AirportResponse])
async def search_airports(q: str = Query("", min_length=0, max_length=50)):
    q_clean = q.strip()
    if len(q_clean) < 2:
        return []
    q_lower = q_clean.lower()
    results = [
        a for a in AIRPORTS
        if a["code"].lower().startswith(q_lower)
        or q_lower in a["city"].lower()
        or q_lower in a["name"].lower()
        or q_lower in a["country"].lower()
    ]
    results.sort(key=lambda a: (
        0 if a["code"].lower().startswith(q_lower) else
        1 if q_lower in a["city"].lower() else
        2
    ))
    return results[:20]
