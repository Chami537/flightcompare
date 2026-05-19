import asyncio
import json
import logging

from asyncio import Queue

logger = logging.getLogger(__name__)


class EventManager:
    """Manages Server-Sent Events connections for pushing updates to clients."""

    def __init__(self):
        self._queues: list[Queue] = []

    def connect(self) -> Queue:
        queue: Queue = Queue()
        self._queues.append(queue)
        logger.info(f"SSE client connected. Total: {len(self._queues)}")
        return queue

    def disconnect(self, queue: Queue):
        if queue in self._queues:
            self._queues.remove(queue)
            logger.info(f"SSE client disconnected. Total: {len(self._queues)}")

    async def send_event(self, event_type: str, data: dict):
        """Broadcast a server-sent event to all connected clients."""
        message = f"event: {event_type}\ndata: {json.dumps(data)}\n\n"
        disconnected = []
        for queue in self._queues:
            try:
                await queue.put(message)
            except Exception:
                disconnected.append(queue)

        for q in disconnected:
            self._queues.remove(q)
