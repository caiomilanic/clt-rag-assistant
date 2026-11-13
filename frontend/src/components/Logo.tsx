import { Scale } from "lucide-react";

export default function Logo({ size = 32 }: { size?: number }) {
  return (
    <span className="grid place-items-center rounded-lg bg-brand text-white" style={{ width: size, height: size }}>
      <Scale size={size * 0.56} strokeWidth={2} />
    </span>
  );
}
