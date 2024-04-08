const express = require('express');
const puppeteer = require('puppeteer');

const app = express();
let browserInstance;

app.use(express.json());

app.post('/generate-pdf', async (req, res) => {
  try {
    if (!browserInstance) {
      browserInstance = await puppeteer.launch({ headless: true });
    }

    const htmlContent = generateHTML(req.body);

    const page = await browserInstance.newPage();
    await page.setContent(htmlContent, { waitUntil: 'domcontentloaded' });
    const pdfBuffer = await page.pdf();

    // Set response headers
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', 'attachment; filename="prescription.pdf"');
    res.send(pdfBuffer);
  } catch (error) {
    console.error('Error generating PDF:', error);
    res.status(500).send('Error generating PDF');
  }
});

const generateHTML = (billingData) => {
  // Generate HTML content using the billing data
  let htmlContent = `
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Invoice</title>
        <!-- Include your CSS styles here -->
    </head>
    <body>
        <!-- Your HTML layout for billing here -->
        <h1>Invoice</h1>
        <p>Patient Name: ${billingData.patientName}</p>
        <!-- Add more HTML content here using billingData -->
    </body>
    </html>
  `;
  return htmlContent;
};

const port = process.env.PORT || 3000;
app.listen(port, () => {
  console.log(`Server is running on port ${port}`);
});
