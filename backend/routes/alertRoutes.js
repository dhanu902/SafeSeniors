const express = require("express");
const router = express.Router();
const Alert = require("../models/Alert");

// POST /api/alerts
router.post("/", async (req, res) => {
  try {
    const { userId, eventType, status, timestamp, confidenceScore } = req.body;

    const alert = new Alert({
      userId,
      eventType,
      status,
      timestamp,
      confidenceScore
    });

    await alert.save();

    res.status(201).json({
      message: "Alert saved successfully",
      alert
    });
  } catch (error) {
    res.status(500).json({
      message: "Error saving alert",
      error: error.message
    });
  }
});

// GET /api/alerts
router.get("/", async (req, res) => {
  try {
    const alerts = await Alert.find().sort({ _id: -1 });
    res.status(200).json(alerts);
  } catch (error) {
    res.status(500).json({
      message: "Error fetching alerts",
      error: error.message
    });
  }
});

// PATCH /api/alerts/:id/acknowledge
router.patch("/:id/acknowledge", async (req, res) => {
  try {
    const updatedAlert = await Alert.findByIdAndUpdate(
      req.params.id,
      { acknowledged: true },
      { new: true }
    );

    if (!updatedAlert) {
      return res.status(404).json({ message: "Alert not found" });
    }

    res.status(200).json({
      message: "Alert acknowledged",
      alert: updatedAlert
    });
  } catch (error) {
    res.status(500).json({
      message: "Error updating alert",
      error: error.message
    });
  }
});

module.exports = router;