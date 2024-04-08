const express = require('express');
const PDFDocument = require('pdfkit');
const bodyParser = require('body-parser'); // Import body-parser module
const { detect } = require('langdetect');

const app = express();

// Map Indian languages to Google Noto font file names
const languageToFont = {
    'en': 'english.ttf',
    'hi': 'hindi.ttf',   // Hindi (Devanagari script)
    'te': 'telugu.ttf',       // Telugu
    'mr': 'marathi.ttf',   // Marathi (Devanagari script)
    'ta': 'tamil.ttf',        // Tamil
    'gu': 'gujarati.ttf',     // Gujarati
    'kn': 'kannada.ttf',      // Kannada
    'ml': 'malayalam.ttf',    // Malayalam
    // Add more Indian languages as needed
};

// Use body-parser middleware to parse JSON data
app.use(bodyParser.json());

// Function to detect language and select appropriate font
function selectFont(text) {
    let lang = 'en'; // Default to English if no language detected
    try {
        const detectionResult = detect(text);
        if (detectionResult.length > 0) {
            lang = detectionResult[0].lang;
            console.log('Detected language:', lang); // Log detected language
        }
    } catch (error) {
        console.error('Error detecting language:', error);
    }
    return languageToFont[lang] || 'english.ttf'; // Default font if language not found
}

// API endpoint to generate and return PDF
app.post('/generate-pdf', (req, res) => {
    const fontFileName = selectFont("English");
    const fontPath = `./noto-fonts/${fontFileName}`;
    const doc = new PDFDocument();
    const { billData } = req.body;
    // Add content to the PDF document
    doc.font(fontPath).fontSize(20).text('Invoice', { align: 'center' }).moveDown(0.5);

    doc.font(fontPath).fontSize(12).text(`Invoice Number: ${billData.invoiceNumber}`).moveDown(0.5);
    doc.text(`Date: ${billData.date}`).moveDown(1);

    doc.font(fontPath).text(`Customer: ${billData.customerName}`).moveDown(0.5);

    // Table header
    doc.font(fontPath).text('Description', 100, doc.y).text('Quantity', 250, doc.y).text('Price', 350, doc.y);
    doc.font(fontPath).moveDown(0.5);

    // Table rows
    let totalPrice = 0;
    billData.items.forEach((item, index) => {
        const { description, quantity, price } = item;
        const y = doc.y;
        doc.text(description, 100, y).text(quantity.toString(), 250, y).text(`$${price.toFixed(2)}`, 350, y);
        totalPrice += price;
    });
    // Total
    doc.moveDown(1).text(`Total: $${totalPrice.toFixed(2)}`, { align: 'right' });
    // Finalize the PDF and close the stream
    res.setHeader('Content-Type', 'application/pdf');
    doc.pipe(res);
    doc.end();
});
// Start the server
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});
