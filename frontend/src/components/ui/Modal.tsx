"use client";

import React from "react";

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 select-none">
      <div className="relative w-full max-w-lg bg-[#2A2432] border border-[#443849] shadow-2xl rounded-md overflow-hidden">
        {/* Modal Header */}
        <div className="h-9 px-3 bg-[#211D29] border-b border-[#443849] flex items-center justify-between">
          <div>
            <span className="text-xs font-mono font-bold text-[#F4EFF6] uppercase">
              {title}
            </span>
          </div>
          <button
            onClick={onClose}
            className="text-xs font-mono text-[#B7ACBD] hover:text-[#F4EFF6] px-1.5 py-0.5 hover:bg-[#332B3C] cursor-pointer rounded-sm"
          >
            [ESC]
          </button>
        </div>

        {/* Subtitle if present */}
        {subtitle && (
          <div className="px-3 py-1.5 bg-[#2A2432] border-b border-[#443849] text-[10px] font-mono text-[#B7ACBD]">
            {subtitle}
          </div>
        )}

        {/* Modal Content */}
        <div className="p-3 text-[#F4EFF6]">{children}</div>
      </div>
    </div>
  );
};
