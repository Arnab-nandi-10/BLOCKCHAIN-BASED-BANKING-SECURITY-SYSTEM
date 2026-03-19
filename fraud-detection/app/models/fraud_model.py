import os
from typing import Optional

import joblib
import numpy as np
import structlog
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import StandardScaler

logger = structlog.get_logger(__name__)

# Number of features expected by all models in this service
FEATURE_DIM: int = 50


class FraudModel:
    """Wrapper around a scikit-learn RandomForest fraud-detection classifier.

    Usage
    -----
    model = FraudModel()
    model.load(settings.MODEL_PATH, settings.SCALER_PATH)
    score, risk_level = model.predict(scaled_features)
    """

    def __init__(self) -> None:
        self.model: Optional[RandomForestClassifier] = None
        self.scaler: Optional[StandardScaler] = None
        self._loaded: bool = False
        self._model_path: str = ""
        self._scaler_path: str = ""

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def load(self, model_path: str, scaler_path: str) -> None:
        """Load model and scaler from *model_path* and *scaler_path*.

        If either file is missing or corrupt, a dummy model is trained
        on synthetic data so the service can start without real artifacts.
        After training the dummy is saved to *model_path* / *scaler_path*
        so subsequent restarts reuse it.
        """
        self._model_path = model_path
        self._scaler_path = scaler_path

        if os.path.exists(model_path) and os.path.exists(scaler_path):
            try:
                self.model = joblib.load(model_path)
                self.scaler = joblib.load(scaler_path)
                self._loaded = True
                logger.info(
                    "Fraud model loaded from disk",
                    model_path=model_path,
                    scaler_path=scaler_path,
                )
                return
            except Exception as exc:
                logger.warning(
                    "Failed to load model from disk — falling back to dummy model",
                    error=str(exc),
                )

        logger.warning(
            "Model artifacts not found — training dummy model",
            model_path=model_path,
            scaler_path=scaler_path,
        )
        self._train_dummy_model()

        # Persist the dummy so the next start is faster
        try:
            artifact_dir = os.path.dirname(model_path)
            if artifact_dir:
                os.makedirs(artifact_dir, exist_ok=True)
            joblib.dump(self.model, model_path)
            joblib.dump(self.scaler, scaler_path)
            logger.info("Dummy model artifacts saved", model_path=model_path)
        except Exception as exc:
            logger.warning("Could not persist dummy model artifacts", error=str(exc))

        self._loaded = True

    def predict(self, scaled_features: np.ndarray) -> tuple[float, str]:
        """Return *(fraud_probability, risk_level)* for pre-scaled features.

        Parameters
        ----------
        scaled_features:
            A 1-D array of length FEATURE_DIM that has **already been
            transformed by self.scaler**.  The ScoringService is responsible
            for scaling before calling this method.
        """
        if self.model is None:
            raise RuntimeError("FraudModel.predict() called before load()")

        features_2d: np.ndarray = scaled_features.reshape(1, -1)
        proba: np.ndarray = self.model.predict_proba(features_2d)

        # predict_proba returns shape (1, n_classes).
        # Class 1 (fraud) probability is at index 1 when two classes present.
        if proba.shape[1] > 1:
            score: float = float(proba[0, 1])
        else:
            score = float(proba[0, 0])

        risk_level: str = self.get_risk_level(score)
        return score, risk_level

    def get_risk_level(self, score: float) -> str:
        """Map a fraud probability score to a categorical risk label."""
        if score < 0.3:
            return "LOW"
        if score < 0.5:
            return "MEDIUM"
        if score < 0.8:
            return "HIGH"
        return "CRITICAL"

    def is_loaded(self) -> bool:
        """Return True once load() has completed successfully."""
        return self._loaded

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _train_dummy_model(self) -> None:
        """Train a minimal RandomForestClassifier on synthetic data.

        Creates a model that can produce valid probability outputs so the
        service remains operational when no real artifacts exist.  The
        classifier is trained on 1 000 samples with 50 features and a ~5 %
        fraud rate; fraud samples have elevated values on the amount and
        unusual-hour features so predictions are directionally sensible.
        """
        rng = np.random.RandomState(42)
        n_samples: int = 1_000

        X: np.ndarray = rng.randn(n_samples, FEATURE_DIM)
        y: np.ndarray = (rng.random(n_samples) < 0.05).astype(int)

        # Make fraud samples harder to miss by shifting key feature dimensions
        fraud_mask = y == 1
        X[fraud_mask, 0] += 3.0   # feature 0 — amount
        X[fraud_mask, 1] += 2.0   # feature 1 — log(amount)
        X[fraud_mask, 2] += 2.0   # feature 2 — is_high_amount
        X[fraud_mask, 3] += 3.0   # feature 3 — is_very_high_amount
        X[fraud_mask, 4] += 2.0   # feature 4 — hour_of_day
        X[fraud_mask, 5] += 3.0   # feature 5 — is_unusual_hour

        self.scaler = StandardScaler()
        X_scaled: np.ndarray = self.scaler.fit_transform(X)

        self.model = RandomForestClassifier(
            n_estimators=20,
            max_depth=6,
            class_weight="balanced",
            random_state=42,
            n_jobs=-1,
        )
        self.model.fit(X_scaled, y)

        logger.info(
            "Dummy fraud model trained",
            n_samples=n_samples,
            n_features=FEATURE_DIM,
            fraud_count=int(y.sum()),
        )
