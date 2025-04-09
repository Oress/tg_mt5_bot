from enum import Enum

class UserRole(str, Enum):
    USER = "user"
    MANAGER = "manager"
    ADMIN = "admin"
    SUPERADMIN = "superadmin"