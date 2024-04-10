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
    return './noto-fonts/' + languageToFont[lang] || './noto-fonts/english.ttf'; // Default font if language not found
}

// API endpoint to generate and return PDF
app.get('/generate-pdf', (req, res) => {
    const fontFileName = selectFont("English");
    const fontPath = `${fontFileName}`;
    const doc = new PDFDocument();
    // const { billData } = req.body;

    const clinicX = 50; // Left side
    const doctorX = 350; // Right side
    const startY = 50; // Initial y-coordinate

    // Add clinic name, address, and contact number on the left side
    doc.font(fontPath).fontSize(12).text('Clinic Name', clinicX, startY);
    doc.font(fontPath).fontSize(10).text('Clinic Address', clinicX, startY + 20); // Adjust y-coordinate as needed
    doc.font(fontPath).fontSize(10).text('Contact Number', clinicX, startY + 40); // Adjust y-coordinate as needed

    // Add doctor name and degrees on the right side
    doc.font(fontPath).fontSize(12).text('Doctor Name', doctorX, startY);
    doc.font(fontPath).fontSize(10).text('Degrees', doctorX, startY + 20); // Adjust y-coordinate as needed

    // Add space before the line
    const spaceBeforeLine = 20; // Adjust the vertical space before the line
    const lineY = Math.max(startY + 60, doc.y + spaceBeforeLine); // Ensure the line starts below the content

    // Add horizontal black line
    const lineWidth = 500; // Adjust the length of the line as needed
    doc.moveTo(clinicX, lineY).lineTo(clinicX + lineWidth, lineY).lineWidth(2).strokeColor('black').stroke();

    // Add patient information
    doc.font(fontPath).fontSize(12).text('Patient Name', clinicX, lineY + 20);
    doc.font(fontPath).fontSize(10).text('Age/Gender :', clinicX, lineY + 40); // Adjust y-coordinate as needed

    // Add doctor name and degrees on the right side
    doc.font(fontPath).fontSize(12).text('Date : 06-02-2024 11:46 AM', doctorX, lineY + 20);
    doc.font(fontPath).fontSize(10).text('Mobile No : 1065378638', doctorX, lineY + 40);

    // Add horizontal black line
    doc.moveTo(clinicX, lineY + 60).lineTo(clinicX + lineWidth, lineY + 60).lineWidth(2).strokeColor('black').stroke();

    // Add space before the symptoms section
    const spaceBeforeSymptoms = 20; // Adjust the vertical space before symptoms
    const symptomsY = lineY + 60 + spaceBeforeSymptoms; // Adjust y-coordinate to position symptoms below patient information

    // Add symptoms details
    const symptomsDetails = [
        "Skin redness or dryness (Since 5 Days with testone characteristics, characteristics 3) (severe) (symp note)",
        "Fever of Unknown Origin (FUO) (Since 1 Days with char ) (severity ) (note) (test one: test)",
        "Blood in vomit (Since 3 Days with Covid Test 55) (high) (blood test)"
    ];
    // Add symptoms details to the PDF
    symptomsDetails.forEach((symptom, index) => {
        doc.font(fontPath).fontSize(10).text(symptom, clinicX, symptomsY + (index + 1) * 20); // Adjust y-coordinate as needed
    });

    // Add space before the clinical findings section
    const spaceBeforeClinicalFindings = 20; // Adjust the vertical space before clinical findings
    const clinicalFindingsY = symptomsY + symptomsDetails.length * 20 + spaceBeforeClinicalFindings; // Adjust y-coordinate to position clinical findings below symptoms

    // Add clinical findings section
    doc.font(fontPath).fontSize(12).text('Clinical Findings :', clinicX, clinicalFindingsY);
    // Add clinical findings details
    const clinicalFindingsDetails = [
        "Clinical Issue (Since 7 Days with char) (high) (note )",
        "Covid 19 (Since 5 Days with Corona ) (high) (Test)"
    ];
    // Add clinical findings details to the PDF
    clinicalFindingsDetails.forEach((finding, index) => {
        doc.font(fontPath).fontSize(10).text(finding, clinicX, clinicalFindingsY + (index + 1) * 20); // Adjust y-coordinate as needed
    });

    // Add space before the diagnosis section
    const spaceBeforeDiagnosis = 20; // Adjust the vertical space before diagnosis
    const diagnosisY = clinicalFindingsY + clinicalFindingsDetails.length * 20 + spaceBeforeDiagnosis; // Adjust y-coordinate to position diagnosis below clinical findings

    // Add diagnosis section
    doc.font(fontPath).fontSize(12).text('Diagnosis :', clinicX, diagnosisY);
    // Add diagnosis details
    const diagnosisDetails = [
        "Testicular dysfunction, unspecified (Rule Out) (test)",
        "Typhoid fever with other complications (Rule Out) (test 1) (test : more, more 1)"
    ];
    // Add diagnosis details to the PDF
    diagnosisDetails.forEach((diagnosis, index) => {
        doc.font(fontPath).fontSize(10).text(diagnosis, clinicX, diagnosisY + (index + 1) * 20); // Adjust y-coordinate as needed
    });

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
