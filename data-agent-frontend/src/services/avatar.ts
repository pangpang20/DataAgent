/**
 * 头像工具类
 */

// 备用头像生成函数
export const generateFallbackAvatar = (): string => {
    const colors = [
        '3B82F6',
        '8B5CF6',
        '10B981',
        'F59E0B',
        'EF4444',
        '6366F1',
        'EC4899',
        '14B8A6',
    ];
    const randomColor = colors[Math.floor(Math.random() * colors.length)];
    const letters = ['AI', '数据', '智能', 'DA', 'BI', 'ML', 'DL', 'NL'];
    const randomLetter = letters[Math.floor(Math.random() * letters.length)];

    const svg = `<svg width="200" height="200" xmlns="http://www.w3.org/2000/svg">
  <rect width="200" height="200" fill="#${randomColor}"/>
  <text x="100" y="120" font-family="Arial, sans-serif" font-size="48" font-weight="bold" text-anchor="middle" fill="white">${randomLetter}</text>
</svg>`;

    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
};

/**
 * 获取头像URL，支持缓存刷新
 * @param url 原始URL
 * @returns 带有时间戳的URL
 */
export const getAvatarUrl = (url: string | undefined): string => {
    if (!url) return '';
    if (url.startsWith('data:')) return url; // Base64 or SVG data
    const separator = url.includes('?') ? '&' : '?';
    return `${url}${separator}t=${new Date().getTime()}`;
};
