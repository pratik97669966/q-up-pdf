const express = require('express');
const puppeteer = require('puppeteer');

const app = express();

app.get('/', async (req, res) => {
    const htmlContent = `
        <html>
            <head>
                <meta charset="UTF-8">
                <title>Medical Prescription</title>
            </head>
            <body>
                <h1>மருத்துவ முன்னோடி</h1>
                <h2>రోగి పేరు: జాన్ డో</h2>
                <p>వయసు: 35</p>
                <p>లింగం: పురుషుడు</p>
                
                <h2>स्वास्थ्य स्थिति:</h2>
                <ul>
                    <li>फीवर</li>
                    <li>सूखी खांसी</li>
                </ul>
                
                <h2>रुग्णाची अवस्था:</h2>
                <ul>
                    <li>ताप</li>
                    <li>कॉफी</li>
                </ul>
                
                <h2>રોગનો વર્ણન:</h2>
                <ul>
                    <li>તાવ</li>
                    <li>ખાંસી</li>
                </ul>
                
                <h2>Doctor's Note:</h2>
                <p>Get plenty of rest. Drink fluids. Follow the prescribed dosage of medicines.</p>
            </body>
        </html>
    `;

    const browser = await puppeteer.launch();
    const page = await browser.newPage();

    await page.setContent(htmlContent, { waitUntil: 'domcontentloaded' });
    const pdfBuffer = await page.pdf();

    await browser.close();

    // Set response headers
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', 'attachment; filename="prescription.pdf"');
    res.send(pdfBuffer);
});

const port = process.env.PORT || 3000;
app.listen(port, () => {
    console.log(`Server is running on port ${port}`);
});
