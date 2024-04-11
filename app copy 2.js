const express = require('express');
const puppeteer = require('puppeteer');
const fs = require('fs'); // Import fs module to read file content

const app = express();
let browserInstance;

app.get('/', async (req, res) => {
    if (!browserInstance) {
        browserInstance = await puppeteer.launch({ headless: true });
    }
    const htmlContent = fs.readFileSync('index.html', 'utf8'); // Read index.html file content
    const page = await browserInstance.newPage();
    await page.setContent(htmlContent, { waitUntil: 'domcontentloaded' });
    const pdfBuffer = await page.pdf();

    // Set response headers
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', 'attachment; filename="prescription.pdf"');
    res.send(pdfBuffer);
});

const port = process.env.PORT || 3000;
app.listen(port, () => {
    console.log(`Server is running on port ${port}`);
});
