const mongoose = require("mongoose");

const alertSchema = new mongoose.Schema({
  userId: {
    type: String,
    required: true
  },
  eventType: {
    type: String,
    required: true
  },
  status: {
    type: String,
    required: true
  },
  timestamp: {
    type: String,
    required: true
  },
  confidenceScore: {
    type: Number,
    required: true
  },
  acknowledged: {
    type: Boolean,
    default: false
  }
});

module.exports = mongoose.model("Alert", alertSchema);