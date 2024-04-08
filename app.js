const express = require('express');
const puppeteer = require('puppeteer');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

app.get('/generate-pdf', async (req, res) => {
    try {
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

        // Launch a headless browser
        const browser = await puppeteer.launch({ args: ['--no-sandbox', '--disable-setuid-sandbox'] });
        const page = await browser.newPage();

        // Set content and options for the PDF
        await page.setContent(htmlContent);
        const pdfBuffer = await page.pdf({ format: 'A4' });

        // Close the browser
        await browser.close();

        // Set response headers
        res.setHeader('Content-Type', 'application/pdf');
        res.setHeader('Content-Disposition', 'inline; filename="medical_prescription.pdf"');

        // Send the PDF as response
        res.send(pdfBuffer);
    } catch (error) {
        console.error('Error generating PDF:', error);
        res.status(500).send('Error generating PDF');
    }
});

app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});
