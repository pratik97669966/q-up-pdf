const express = require('express');
const PDFDocument = require('pdfkit');
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

// Function to detect language and select appropriate font
function selectFont(text) {
    const lang = detect(text);
    return languageToFont[lang] || 'english.ttf'; // Default font if language not found
}

// API endpoint to generate and return PDF
app.get('/generate-pdf', (req, res) => {
    // Create a new PDF document
    const doc = new PDFDocument();

    // Text to render
    const text = 'மருத்துவ!123'; // Hindi text

    // Select appropriate font based on language
    // const fontFileName = selectFont(text);
    const fontPath = `./noto-fonts/tamil.ttf`;

    // Set font and render text
    doc.font(fontPath).fontSize(24).text(text, 100, 100);

    // Pipe the PDF document to the response
    res.setHeader('Content-Type', 'application/pdf');
    doc.pipe(res);
    doc.end();
});

// Start the server
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});
