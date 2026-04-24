from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from typing import Any, Optional

import joblib
import numpy as np
import structlog
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import StandardScaler

from app.core.config import settings
from app.services.feature_engineering import FEATURE_NAMES, TOTAL_FEATURES

logger = structlog.get_logger(__name__)


class FraudModel:
    """Wrapper around the scikit-learn classifier and scaler artifacts."""

    def __init__(self) -> None:
        self.model: Optional[RandomForestClassifier] = None
        self.scaler: Optional[StandardScaler] = None
        self._loaded: bool = False
        self._model_path: str = ""
        self._scaler_path: str = ""
        self._metadata: dict[str, Any] = {}
        self.version: str = settings.MODEL_VERSION

    def load(self, model_path: str, scaler_path: str, metadata_path: str) -> None:
        self._model_path = model_path
        self._scaler_path = scaler_path

        if os.path.exists(model_path) and os.path.exists(scaler_path):
            try:
                self.model = joblib.load(model_path)
                self.scaler = joblib.load(scaler_path)
                self._metadata = self._load_metadata(metadata_path)
                self._validate_loaded_artifacts()
                self.version = str(self._metadata.get("version", settings.MODEL_VERSION))
                self._loaded = True
                logger.info(
                    "Fraud model loaded from disk",
                    model_path=model_path,
                    scaler_path=scaler_path,
                    model_version=self.version,
                )
                return
            except Exception as exc:
                logger.warning(
                    "Failed to load model artifacts; training fallback model",
                    error=str(exc),
                )

        logger.warning(
            "Model artifacts not found; training fallback model",
            model_path=model_path,
            scaler_path=scaler_path,
        )
        self._train_dummy_model()
        self._metadata = {
            "version": f"{settings.MODEL_VERSION}-fallback",
            "trainedAt": datetime.now(timezone.utc).isoformat(),
            "featureCount": TOTAL_FEATURES,
            "featureNames": FEATURE_NAMES,
            "featureSchemaVersion": settings.FEATURE_SCHEMA_VERSION,
            "artifactType": "fallback",
        }
        self.version = str(self._metadata["version"])
        self._persist_artifacts(model_path, scaler_path, metadata_path)
        self._loaded = True

    def predict(self, raw_features: np.ndarray) -> float:
        if self.model is None or self.scaler is None:
            raise RuntimeError("FraudModel.predict() called before load()")
        if raw_features.shape != (TOTAL_FEATURES,):
            raise ValueError(
                f"Expected feature vector shape ({TOTAL_FEATURES},), got {tuple(raw_features.shape)}"
            )

        scaler_features = getattr(self.scaler, "n_features_in_", TOTAL_FEATURES)
        model_features = getattr(self.model, "n_features_in_", TOTAL_FEATURES)
        if scaler_features != TOTAL_FEATURES or model_features != TOTAL_FEATURES:
            raise RuntimeError(
                "Loaded fraud model artifacts are incompatible with the current feature vector."
            )

        scaled = self.scaler.transform(raw_features.reshape(1, -1))
        proba = self.model.predict_proba(scaled)
        if proba.shape[1] > 1:
            return float(proba[0, 1])
        return float(proba[0, 0])

    def is_loaded(self) -> bool:
        return self._loaded

    def metadata(self) -> dict[str, Any]:
        return dict(self._metadata)

    def _load_metadata(self, metadata_path: str) -> dict[str, Any]:
        if not os.path.exists(metadata_path):
            return {
                "version": settings.MODEL_VERSION,
                "featureCount": TOTAL_FEATURES,
                "featureNames": FEATURE_NAMES,
                "featureSchemaVersion": settings.FEATURE_SCHEMA_VERSION,
            }
        with open(metadata_path, "r", encoding="utf-8") as handle:
            return json.load(handle)

    def _validate_loaded_artifacts(self) -> None:
        metadata_feature_count = int(self._metadata.get("featureCount", TOTAL_FEATURES))
        metadata_feature_names = self._metadata.get("featureNames", FEATURE_NAMES)
        metadata_schema_version = str(
            self._metadata.get("featureSchemaVersion", "")
        ).strip()
        scaler_features = getattr(self.scaler, "n_features_in_", TOTAL_FEATURES)
        model_features = getattr(self.model, "n_features_in_", TOTAL_FEATURES)

        if metadata_feature_count != TOTAL_FEATURES:
            raise ValueError(
                f"Model metadata expects {metadata_feature_count} features, service provides {TOTAL_FEATURES}"
            )
        if list(metadata_feature_names) != FEATURE_NAMES:
            raise ValueError("Model metadata feature names do not match the live feature contract")
        if metadata_schema_version != settings.FEATURE_SCHEMA_VERSION:
            raise ValueError(
                "Model feature schema version does not match the live fraud feature contract"
            )
        if scaler_features != TOTAL_FEATURES or model_features != TOTAL_FEATURES:
            raise ValueError("Loaded scaler/model artifacts do not match the expected feature count")

    def _persist_artifacts(self, model_path: str, scaler_path: str, metadata_path: str) -> None:
        try:
            artifact_dir = os.path.dirname(model_path)
            if artifact_dir:
                os.makedirs(artifact_dir, exist_ok=True)
            joblib.dump(self.model, model_path)
            joblib.dump(self.scaler, scaler_path)
            with open(metadata_path, "w", encoding="utf-8") as handle:
                json.dump(self._metadata, handle, indent=2)
        except Exception as exc:
            logger.warning("Could not persist fallback model artifacts", error=str(exc))

    def _train_dummy_model(self) -> None:
        rng = np.random.default_rng(42)
        n_samples = 3_000
        X = rng.normal(loc=0.0, scale=1.0, size=(n_samples, TOTAL_FEATURES))

        # Simulate a modest fraud prior with stronger signal on amount, velocity, and rule-like features.
        base = rng.uniform(0.0, 0.08, size=n_samples)
        base += np.clip(X[:, 0], 0.0, None) * 0.04
        base += np.clip(X[:, 3], 0.0, None) * 0.08
        base += np.clip(X[:, 5], 0.0, None) * 0.10
        base += np.clip(X[:, 11], 0.0, None) * 0.06
        base += np.clip(X[:, 39], 0.0, None) * 0.10
        base += np.clip(X[:, 47], 0.0, None) * 0.06
        base += np.clip(X[:, 51], 0.0, None) * 0.07
        y = (rng.random(n_samples) < np.clip(base, 0.0, 0.95)).astype(int)

        self.scaler = StandardScaler()
        X_scaled = self.scaler.fit_transform(X)
        self.model = RandomForestClassifier(
            n_estimators=120,
            max_depth=10,
            min_samples_leaf=4,
            class_weight="balanced_subsample",
            random_state=42,
            n_jobs=-1,
        )
        self.model.fit(X_scaled, y)
        logger.info(
            "Fallback fraud model trained",
            samples=n_samples,
            feature_count=TOTAL_FEATURES,
            fraud_rate=float(y.mean()),
        )
