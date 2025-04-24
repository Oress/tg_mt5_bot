import logging
import uuid
from datetime import datetime
from decimal import Decimal

from sqlalchemy import select

from tg_server.database.models import TgUser, Order

logger = logging.getLogger(__name__)


class OrderService:
    def __init__(self, session):
        self.session = session

    def save_close_order_to_db(self, user_id: int, city: str, acc_number: int, user_eur_amount: Decimal, order_result) -> Order:
        if order_result is None:
            logger.error(f"[OrderService] Order result is None for user_id: {user_id}")

        datetime_now = datetime.utcnow()
        new_order = Order(
            id=uuid.uuid4(),
            user_id=user_id,
            city=city,
            acc_number=acc_number,
            instrument=order_result.request.symbol,
            deal=order_result.deal,
            order=order_result.order,
            ask=order_result.ask,
            bid=order_result.bid,
            volume=order_result.volume,
            type='short_close',
            euro_amount=user_eur_amount,
            price=order_result.price,
            created_at=datetime_now,
        )
        self.session.add(new_order)
        return new_order

    def save_short_order_to_db(self, user_id: int, city: str, acc_number: int, user_eur_amount: Decimal, order_result) -> Order:
        if order_result is None:
            logger.error(f"[OrderService] Order result is None for user_id: {user_id}")

        datetime_now = datetime.utcnow()
        new_order = Order(
            id=uuid.uuid4(),
            user_id=user_id,
            city=city,
            acc_number=acc_number,
            instrument=order_result.request.symbol,
            deal=order_result.deal,
            order=order_result.order,
            ask=order_result.ask,
            bid=order_result.bid,
            volume=order_result.volume,
            type='short',
            euro_amount=user_eur_amount,
            price=order_result.price,
            created_at=datetime_now,
        )
        self.session.add(new_order)
        return new_order

    def find_recent_order_by_user_id(self, user_id) -> Order:
        stmt = select(Order).where(Order.user_id == user_id).order_by(Order.created_at.desc())
        most_recent_order = self.session.execute(stmt).first()
        return most_recent_order.Order if most_recent_order else None