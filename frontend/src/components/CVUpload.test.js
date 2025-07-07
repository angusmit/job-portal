import { fireEvent, render, screen } from '@testing-library/react';
import CVUpload from './CVUpload';

test('renders upload button', () => {
    render(<CVUpload />);
    const uploadButton = screen.getByText(/Upload CV/i);
    expect(uploadButton).toBeInTheDocument();
});

test('shows error for invalid file type', () => {
    render(<CVUpload />);
    const input = screen.getByLabelText(/Choose a file/i);

    const file = new File(['test'], 'test.txt', { type: 'text/plain' });
    fireEvent.change(input, { target: { files: [file] } });

    expect(screen.getByText(/Please select a PDF or DOCX file/i)).toBeInTheDocument();
});