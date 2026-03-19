from typing import Dict
import base64

import jwt
from jwt.exceptions import InvalidTokenError as JWTError
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import settings

# Reusable FastAPI security scheme — extracts Bearer token from Authorization header
security = HTTPBearer()


def verify_token(token: str) -> Dict:
    """Decode and verify a JWT token using HS256 and JWT_SECRET.

    Returns the decoded claims dict on success.
    Raises HTTP 401 if the token is invalid, expired, or tampered.
    """
    try:
        # Decode Base64 secret to match Java services (they use Base64.getDecoder().decode())
        decoded_secret = base64.b64decode(settings.JWT_SECRET)
        payload: Dict = jwt.decode(
            token,
            decoded_secret,
            algorithms=["HS512", "HS384", "HS256"],
        )
        return payload
    except JWTError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Invalid or expired token: {exc}",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc


def get_tenant_id(token: str) -> str:
    """Extract the tenantId claim from a JWT token.

    Raises HTTP 401 if the claim is absent.
    """
    claims = verify_token(token)
    tenant_id: str | None = claims.get("tenantId")
    if not tenant_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token is missing required 'tenantId' claim",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return tenant_id


async def get_current_token(
    credentials: HTTPAuthorizationCredentials = Depends(security),
) -> Dict:
    """FastAPI dependency that validates the Bearer token and returns decoded claims."""
    return verify_token(credentials.credentials)
