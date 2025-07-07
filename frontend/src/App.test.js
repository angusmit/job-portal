import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import CVUpload from './components/CVUpload';

// Mock axios
jest.mock('axios');

describe('Job Portal Tests', () => {
  test('CV Upload Component', async () => {
    render(
      <BrowserRouter>
        <CVUpload />
      </BrowserRouter>
    );

    const fileInput = screen.getByLabelText(/choose a file/i);
    const uploadButton = screen.getByText(/upload cv/i);

    // Test file selection
    const file = new File(['test'], 'test.pdf', { type: 'application/pdf' });
    fireEvent.change(fileInput, { target: { files: [file] } });

    // Test upload
    fireEvent.click(uploadButton);

    await waitFor(() => {
      expect(screen.getByText(/uploading/i)).toBeInTheDocument();
    });
  });
});