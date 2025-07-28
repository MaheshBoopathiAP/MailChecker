import { useState } from "react";
import * as XLSX from "xlsx";
import uploadIcon from "../assets/upload-icon.png";

function ExcelCsvUploader() {
  const [data, setData] = useState([]);
  const [fileName, setFileName] = useState("");
  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [successMsg, setSuccessMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState("");

  const desiredHeaders = [
    "First Name",
    "Last Name",
    "Title",
    "Company",
    "Company Name for Emails",
    "Email",
    "Email Status",
    "Primary Email Source",
  ];

  const handleFileChange = (event) => {
    const selectedFile = event.target.files[0];
    if (!selectedFile) return;

    setFileName(selectedFile.name);
    setFile(selectedFile);
    setSuccessMsg("");
    setErrorMsg("");

    const reader = new FileReader();
    reader.onload = (e) => {
      const fileData = e.target.result;
      let workbook;

      try {
        if (selectedFile.name.endsWith(".csv")) {
          workbook = XLSX.read(fileData, { type: "binary" });
        } else {
          workbook = XLSX.read(fileData, { type: "array" });
        }
      } catch (err) {
        setErrorMsg("Failed to read the file. Please upload a valid Excel or CSV file.");
        setData([]);
        return;
      }

      const wsname = workbook.SheetNames[0];
      const ws = workbook.Sheets[wsname];
      const jsonData = XLSX.utils.sheet_to_json(ws, { header: 1 });

      if (jsonData.length === 0) {
        setData([]);
        setErrorMsg("The file is empty or unreadable.");
        return;
      }

      const headerRow = jsonData[0];
      const columnsToShow = desiredHeaders
        .map((header) => headerRow.indexOf(header))
        .filter((idx) => idx !== -1);

      if (columnsToShow.length === 0) {
        setErrorMsg("None of the specified headers were found in the file.");
        setData([]);
        return;
      }

      const filteredData = jsonData.map((row) =>
        columnsToShow.map((colIndex) => (row[colIndex] !== undefined ? row[colIndex] : ""))
      );

      setData(filteredData);
    };

    if (selectedFile.name.endsWith(".csv")) {
      reader.readAsBinaryString(selectedFile);
    } else {
      reader.readAsArrayBuffer(selectedFile);
    }
  };

  const handleSend = async () => {
     if (!file) return;

     setLoading(true);
     setSuccessMsg("");
     setErrorMsg("");

     try {
       const formData = new FormData();
       formData.append("file", file);

       const response = await fetch("http://localhost:9999/api/upload", {
         method: "POST",
         body: formData,
       });

       if (!response.ok) {
         const errorText = await response.text();
         setErrorMsg(`Failed to send file: ${errorText || response.statusText}`);
         setLoading(false);
         return;
       }

       // Expect the backend to send a file (e.g., xlsx) as a Blob
       const blob = await response.blob();

       // Get filename from Content-Disposition header, if provided
       let downloadFileName = "filtered_output.xlsx";
       const contentDisposition = response.headers.get("Content-Disposition");
       if (contentDisposition) {
         const match = contentDisposition.match(/filename="?([^"]+)"?/);
         if (match && match[1]) {
           downloadFileName = match[1];
         }
       }

       // Create a blob download link and click it to trigger download in the browser
       const url = window.URL.createObjectURL(blob);
       const a = document.createElement("a");
       a.href = url;
       a.download = downloadFileName;
       document.body.appendChild(a);
       a.click();
       a.remove();
       window.URL.revokeObjectURL(url);

       setSuccessMsg("File sent successfully! Download should start automatically.");
     } catch (error) {
       setErrorMsg(`Error sending file: ${error.message}`);
     } finally {
       setLoading(false);
     }
   };


  return (
    <div className="min-h-screen bg-gray-50 w-full">
      {/* Navbar */}
      <nav className="bg-white shadow-md w-full">
        <div className="w-full px-6 py-4 flex justify-between items-center">
          <div className="text-xl font-bold text-green-600">SentMailChecker</div>
{/*           <div className="space-x-6 text-gray-600"> */}
{/*             <a href="#upload" className="hover:text-green-600">Upload</a> */}
{/*             <a href="#preview" className="hover:text-green-600">Preview</a> */}
{/*             <a href="#about" className="hover:text-green-600">About</a> */}
{/*           </div> */}
        </div>
      </nav>

      {/* Main content */}
      <div className="w-full px-10 py-6">
        {/* Input and Send button on same level, filling entire width */}
        <div className="w-full flex items-center space-x-4 mb-6 px-0">
          {/* File Upload Label */}
          <label
            htmlFor="file-upload"
            className="flex flex-1 items-center justify-start w-full p-4 border-2 border-dashed border-gray-300 rounded-lg cursor-pointer hover:border-green-600 hover:bg-green-50 transition"
          >
           <img src={uploadIcon} alt="Upload" className="w-8 h-8 mr-2" />
            <span className="text-gray-700 truncate">
              {fileName ? `Selected: ${fileName}` : "Click to upload Excel (.xlsx/.xls) or CSV file"}
            </span>
            <input
              id="file-upload"
              type="file"
              accept=".xlsx,.xls,.csv"
              className="hidden"
              onChange={handleFileChange}
            />
          </label>
          {/* Send Button */}
          <button
            onClick={handleSend}
            disabled={!file || loading}
            className="bg-green-600 text-white py-2 px-8 rounded shadow-lg hover:bg-green-700 transition font-semibold disabled:opacity-50 whitespace-nowrap"
          >
            {loading ? "Sending..." : "Send"}
          </button>
        </div>

        {/* Success / Error Messages */}
        {successMsg && <p className="mb-4 text-green-700 font-medium">{successMsg}</p>}
        {errorMsg && <p className="mb-4 text-red-600 font-medium">{errorMsg}</p>}

        {/* Preview Table */}
        {data.length > 0 && (
          <div
            id="preview"
            className="overflow-x-auto rounded border border-gray-200 shadow shadow-green-500/50 w-full"
          >
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="bg-green-100">
                <tr>
                  {data[0].map((headerCell, idx) => (
                    <th key={idx} className="px-4 py-2 font-semibold text-gray-700">
                      {headerCell}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {data.slice(1, 11).map((row, rowIndex) => (
                  <tr key={rowIndex} className="odd:bg-white even:bg-green-50">
                    {row.map((cell, cellIndex) => (
                      <td key={cellIndex} className="px-4 py-2 whitespace-pre-wrap">
                        {cell}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="text-xs text-gray-400 mt-2 mb-4 text-center">
              Showing first 10 rows of specified columns. Upload a new file to preview more.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

export default ExcelCsvUploader;
