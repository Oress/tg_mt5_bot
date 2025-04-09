from cryptography.fernet import Fernet, MultiFernet
import os

class PasswordEncryptor:
    def __init__(self, env_var: str = "ENCRYPTION_KEY"):
        # Read the primary key from the environment variable
        key = os.getenv(env_var)
        if not key:
            raise ValueError(f"Environment variable '{env_var}' is not set or empty.")

        # Support multiple keys for migration
        self.keys = [Fernet(k) for k in key.split(",")]
        self.fernet = MultiFernet(self.keys)

    def encrypt(self, password: bytes) -> bytes:
        """Encrypts a password."""
        return self.fernet.encrypt(password)

    def decrypt(self, token: bytes) -> bytes:
        """Decrypts an encrypted password."""
        return self.fernet.decrypt(token)

    def rotate(self, token: bytes) -> bytes:
        """Rotates the encryption to use the latest key."""
        return self.fernet.rotate(token)

pwd_encryptor = PasswordEncryptor()
