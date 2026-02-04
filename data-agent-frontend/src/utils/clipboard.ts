/**
 * Clipboard utility functions with fallback support
 */

/**
 * Copy text to clipboard with fallback for older browsers
 * @param text Text to copy
 * @returns Promise<boolean> Success status
 */
export async function copyToClipboard(text: string): Promise<boolean> {
    // Method 1: Modern Clipboard API (requires HTTPS or localhost)
    if (navigator.clipboard && navigator.clipboard.writeText) {
        try {
            await navigator.clipboard.writeText(text);
            return true;
        } catch (err) {
            console.warn('Clipboard API failed, trying fallback method:', err);
            // Fall through to fallback method
        }
    }

    // Method 2: Fallback using execCommand (works in more environments)
    try {
        return copyToClipboardFallback(text);
    } catch (err) {
        console.error('All clipboard methods failed:', err);
        return false;
    }
}

/**
 * Fallback method using document.execCommand
 * @param text Text to copy
 * @returns boolean Success status
 */
function copyToClipboardFallback(text: string): boolean {
    // Create a temporary textarea element
    const textarea = document.createElement('textarea');
    textarea.value = text;

    // Make it invisible but still accessible for selection
    textarea.style.position = 'fixed';
    textarea.style.top = '-9999px';
    textarea.style.left = '-9999px';
    textarea.style.opacity = '0';

    document.body.appendChild(textarea);

    try {
        // Select the text
        textarea.select();
        textarea.setSelectionRange(0, text.length);

        // Execute copy command
        const successful = document.execCommand('copy');

        if (!successful) {
            throw new Error('execCommand returned false');
        }

        return true;
    } catch (err) {
        console.error('Fallback copy method failed:', err);
        return false;
    } finally {
        // Clean up
        document.body.removeChild(textarea);
    }
}

/**
 * Check if clipboard API is available
 * @returns boolean
 */
export function isClipboardAvailable(): boolean {
    return !!(navigator.clipboard && navigator.clipboard.writeText);
}
