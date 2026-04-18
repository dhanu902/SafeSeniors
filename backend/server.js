const express = require("express");
const mongoose = require("mongoose");
const cors = require("cors");
require("dotenv").config();

const alertRoutes = require("./routes/alertRoutes");

const app = express();

app.use(cors());
app.use(express.json());

mongoose.connect(process.env.MONGO_URI)
  .then(() => console.log("MongoDB Connected"))
  .catch(err => console.log(err));

// Test route
app.get("/", (req, res) => {
    res.send("SafeSeniors Backend Running");
});

app.use("/api/alerts", alertRoutes);

const PORT = process.env.PORT || 5050;

app.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});