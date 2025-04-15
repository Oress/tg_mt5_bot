import logging
import uuid
from datetime import datetime

from tg_server.database.models import TgUser, Order

logger = logging.getLogger(__name__)


class OrderService:
    def __init__(self, session):
        self.session = session

    def save_order_to_db(self, user_id: int, city: str, order_result) -> Order:
        if order_result is None:
            logger.error(f"[OrderService] Order result is None for user_id: {user_id}")

        datetime_now = datetime.utcnow()
        new_order = Order(
            id=uuid.uuid4(),
            user_id=user_id,
            city=city,
            deal=order_result.deal,
            order=order_result.order,
            ask=order_result.ask,
            bid=order_result.bid,
            volume=order_result.volume,
            price=order_result.price,
            created_at=datetime_now,
        )
        self.session.add(new_order)
        return new_order