(function() {
    // 1. Umami Analytics
    const umamiScript = document.createElement('script');
    umamiScript.defer = true;
    umamiScript.src = "https://umami.runnable.run/script.js";
    umamiScript.dataset.websiteId = "58980c46-67b3-4ec1-bed9-a268e8ac1af3";
    document.head.appendChild(umamiScript);

    // 2. Favicon
    if (!document.querySelector('link[rel="icon"]')) {
        const favicon = document.createElement('link');
        favicon.rel = "icon";
        favicon.type = "image/svg+xml";
        favicon.href = "assets/favicon.svg";
        document.head.appendChild(favicon);
    }

    // 3. Google Fonts
    // Preconnect links
    const preconnect1 = document.createElement('link');
    preconnect1.rel = "preconnect";
    preconnect1.href = "https://fonts.googleapis.com";
    document.head.appendChild(preconnect1);

    const preconnect2 = document.createElement('link');
    preconnect2.rel = "preconnect";
    preconnect2.href = "https://fonts.gstatic.com";
    preconnect2.crossOrigin = "";
    document.head.appendChild(preconnect2);

    // Font Stylesheet
    const fontLink = document.createElement('link');
    fontLink.rel = "stylesheet";
    // 合并所有页面用到的字体：Noto Sans JP, Orbitron, Syncopate
    fontLink.href = "https://fonts.googleapis.com/css2?family=Noto+Sans+JP:wght@400;700;900&family=Orbitron:wght@400;700;900&family=Syncopate:wght@400;700&display=swap";
    document.head.appendChild(fontLink);

})();
