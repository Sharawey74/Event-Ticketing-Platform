import pdfplumber
import sys
import os

pdf_files = [
    "Phase1A_Section 2_ExecutionMap.pdf",
    "Phase1A_Sections 3,4,5_FullStructure.pdf",
    "Phase1A_Sections 6,7,8,9_ImplementationGuides.pdf",
    "Phase1A_Sections 10, 11, 12_Testing_Deployment_Fundamentals.docx.pdf",
    "Phase1A_Sections 13, 14, 15_16_Practices_Resources_Troubleshooting_Transition.pdf"
]

output_dir = "extracted_text"
os.makedirs(output_dir, exist_ok=True)

for pdf_file in pdf_files:
    print(f"Processing: {pdf_file}")
    output_file = os.path.join(output_dir, pdf_file.replace('.pdf', '.txt'))
    
    try:
        with pdfplumber.open(pdf_file) as pdf:
            text_content = []
            for i, page in enumerate(pdf.pages):
                text = page.extract_text()
                if text:
                    text_content.append(f"--- PAGE {i+1} ---\n{text}\n")
            
            with open(output_file, 'w', encoding='utf-8') as f:
                f.write('\n'.join(text_content))
            
            print(f"  ✓ Extracted {len(pdf.pages)} pages to {output_file}")
    except Exception as e:
        print(f"  ✗ Error: {e}")

print("\nExtraction complete!")
